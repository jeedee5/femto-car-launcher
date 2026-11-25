package io.github.seijikohara.femto.data.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.media.MediaBrowserService
import android.util.Log
import androidx.core.content.getSystemService
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.seijikohara.femto.data.common.queryIntentServicesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The music sources panel: a row of the player apps the driver picked (Spotify,
 * the head unit's radio and Bluetooth music, ...) plus the app that played last,
 * each one tap away from playing. Kept apart from the music card's own files so
 * the panel stays a self-contained addition on top of upstream.
 */

private const val TAG = "MusicSources"

private val Context.musicSourcesDataStore: DataStore<Preferences> by preferencesDataStore(name = "music_sources")

private val PACKAGES_KEY = stringPreferencesKey("packages")
private val SHOW_PANEL_KEY = booleanPreferencesKey("show_panel")

// Package names never contain a newline, so it separates the ordered list.
private const val PACKAGE_SEPARATOR = "\n"

private const val PAUSE_SETTLE_MS = 2_000L
private const val PAUSE_POLL_MS = 100L

/** The driver's picks in their order, and whether the dashboard shows the panel at all. */
internal data class MusicSourcesSettings(
    val packages: List<String> = emptyList(),
    val showPanel: Boolean = true,
)

/** DataStore-backed store of [MusicSourcesSettings]. */
internal class MusicSourcesPreferences(
    private val context: Context,
) {
    val settings: Flow<MusicSourcesSettings> =
        context.musicSourcesDataStore.data
            .map { prefs ->
                MusicSourcesSettings(
                    packages =
                        prefs[PACKAGES_KEY]
                            ?.split(PACKAGE_SEPARATOR)
                            ?.filter { it.isNotBlank() }
                            .orEmpty(),
                    showPanel = prefs[SHOW_PANEL_KEY] ?: true,
                )
            }.distinctUntilChanged()

    /** Add [packageName] at the end of the picks, or remove it, keeping the order of the others. */
    suspend fun setPicked(
        packageName: String,
        picked: Boolean,
    ) = editPackages { packages ->
        packages.filterNot { it == packageName } + listOfNotNull(packageName.takeIf { picked })
    }

    suspend fun setShowPanel(value: Boolean) {
        runCatching { context.musicSourcesDataStore.edit { it[SHOW_PANEL_KEY] = value } }
            .onFailure { Log.w(TAG, "could not save the panel toggle", it) }
    }

    private suspend fun editPackages(transform: (List<String>) -> List<String>) {
        runCatching {
            context.musicSourcesDataStore.edit { prefs ->
                val current = prefs[PACKAGES_KEY]?.split(PACKAGE_SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
                prefs[PACKAGES_KEY] = transform(current).joinToString(PACKAGE_SEPARATOR)
            }
        }.onFailure { Log.w(TAG, "could not save the music sources", it) }
    }
}

/**
 * The packages the panel shows, in order: the picks in the driver's order, with the app that played last in front
 * when it is not one of them, so the panel always offers a way back to it. Duplicates and [ownPackage] are dropped.
 */
internal fun musicSourceOrder(
    picks: List<String>,
    lastUsed: String?,
    ownPackage: String,
): List<String> =
    (listOfNotNull(lastUsed?.takeIf { it !in picks }) + picks)
        .distinct()
        .filterNot { it == ownPackage }

/**
 * Starts a picked source. A source with a media browser service (Spotify and most streaming apps) is asked to play
 * through [MediaResumer] without leaving the dashboard; one without (a head unit's radio or Bluetooth music app,
 * which the play media key could not reach reliably) is opened directly.
 */
internal class MusicSourceSwitcher(
    private val context: Context,
    private val resumer: MediaResumer,
    private val openApp: (String) -> Boolean,
) {
    private val sessionManager: MediaSessionManager? = context.getSystemService()
    private val listener = ComponentName(context, MusicSessionListenerService::class.java)

    suspend fun play(packageName: String) {
        // The resume chain stops as soon as another app plays, so the current source is paused first.
        pauseOthers(packageName)
        if (hasBrowserService(packageName)) {
            val outcome = resumer.resume(packageName)
            Log.i(TAG, "source $packageName: $outcome")
        } else if (!openApp(packageName)) {
            Log.w(TAG, "source $packageName has no launch activity")
        }
    }

    private suspend fun pauseOthers(packageName: String) {
        val playing = otherPlayingSessions(packageName)
        if (playing.isEmpty()) return
        playing.forEach { runCatching { it.transportControls.pause() } }
        // The pause lands asynchronously; wait until the sessions report it so the resume chain does not see the
        // old source still playing and give up.
        withTimeoutOrNull(PAUSE_SETTLE_MS) {
            while (otherPlayingSessions(packageName).isNotEmpty()) delay(PAUSE_POLL_MS)
        } ?: Log.w(TAG, "the playing source did not pause within $PAUSE_SETTLE_MS ms")
    }

    private fun otherPlayingSessions(packageName: String): List<MediaController> =
        runCatching {
            sessionManager
                ?.getActiveSessions(listener)
                .orEmpty()
                .filter { it.packageName != packageName && it.playbackState?.state == PlaybackState.STATE_PLAYING }
        }.onFailure { Log.w(TAG, "could not read the active sessions", it) }
            .getOrDefault(emptyList())

    private suspend fun hasBrowserService(packageName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .queryIntentServicesCompat(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(packageName))
                    .isNotEmpty()
            }.getOrDefault(false)
        }
}
