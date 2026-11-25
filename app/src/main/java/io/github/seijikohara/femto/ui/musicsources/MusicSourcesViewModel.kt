package io.github.seijikohara.femto.ui.musicsources

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seijikohara.femto.data.apps.AppEntry
import io.github.seijikohara.femto.data.apps.AppsRepository
import io.github.seijikohara.femto.data.common.WhileUiSubscribed
import io.github.seijikohara.femto.data.music.LastPlayedPreferences
import io.github.seijikohara.femto.data.music.MediaResumer
import io.github.seijikohara.femto.data.music.MusicSourceSwitcher
import io.github.seijikohara.femto.data.music.MusicSourcesPreferences
import io.github.seijikohara.femto.data.music.MusicSourcesSettings
import io.github.seijikohara.femto.data.music.PlatformResumePort
import io.github.seijikohara.femto.data.music.musicSourceOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "MusicSourcesViewModel"

/** One app on the panel or in the picker. */
internal data class MusicSourceApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

/**
 * The panel's state: whether it shows, the [tiles] it offers (the picks in order, the last used app in front when it
 * is not picked), which of them played last ([lastUsed]), the app a tap is starting ([pending]), and every launchable
 * app for the picker ([installed], picks first).
 */
internal data class MusicSourcesUiState(
    val showPanel: Boolean = false,
    val tiles: List<MusicSourceApp> = emptyList(),
    val picked: Set<String> = emptySet(),
    val lastUsed: String? = null,
    val pending: String? = null,
    val installed: List<MusicSourceApp> = emptyList(),
)

internal sealed interface MusicSourcesAction {
    data class Play(
        val packageName: String,
    ) : MusicSourcesAction

    data class SetPicked(
        val packageName: String,
        val picked: Boolean,
    ) : MusicSourcesAction

    data class SetShowPanel(
        val show: Boolean,
    ) : MusicSourcesAction
}

internal class MusicSourcesViewModel(
    settings: Flow<MusicSourcesSettings>,
    lastUsed: Flow<String?>,
    installedApps: Flow<List<AppEntry>>,
    ownPackage: String,
    private val setPicked: suspend (String, Boolean) -> Unit,
    private val setShowPanel: suspend (Boolean) -> Unit,
    private val play: suspend (String) -> Unit,
) : ViewModel() {
    private val pending = MutableStateFlow<String?>(null)

    private val apps: Flow<List<MusicSourceApp>> =
        installedApps
            .map { entries ->
                entries
                    .filterNot { it.componentName.packageName == ownPackage }
                    .distinctBy { it.componentName.packageName }
                    .map { MusicSourceApp(it.componentName.packageName, it.label, it.icon.asImageBitmap()) }
            }.catch { error ->
                Log.w(TAG, "could not list the installed apps", error)
                emit(emptyList())
            }

    val uiState: StateFlow<MusicSourcesUiState> =
        combine(settings, lastUsed.distinctUntilChanged(), apps, pending) { prefs, last, installed, starting ->
            val byPackage = installed.associateBy { it.packageName }
            MusicSourcesUiState(
                showPanel = prefs.showPanel,
                tiles = musicSourceOrder(prefs.packages, last, ownPackage).mapNotNull { byPackage[it] },
                picked = prefs.packages.toSet(),
                lastUsed = last,
                pending = starting,
                installed =
                    prefs.packages.mapNotNull { byPackage[it] } +
                        installed.filterNot { it.packageName in prefs.packages },
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, MusicSourcesUiState())

    fun onAction(action: MusicSourcesAction) {
        when (action) {
            is MusicSourcesAction.Play -> start(action.packageName)
            is MusicSourcesAction.SetPicked -> viewModelScope.launch { setPicked(action.packageName, action.picked) }
            is MusicSourcesAction.SetShowPanel -> viewModelScope.launch { setShowPanel(action.show) }
        }
    }

    // One start at a time: a second tap while a source is still starting would pause the one just asked to play.
    private fun start(packageName: String) {
        if (!pending.compareAndSet(null, packageName)) return
        viewModelScope.launch {
            try {
                play(packageName)
            } finally {
                pending.value = null
            }
        }
    }
}

internal val MusicSourcesViewModelFactory: ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
            val preferences = MusicSourcesPreferences(application)
            val lastPlayed = LastPlayedPreferences(application)
            val apps = AppsRepository(application)
            val switcher =
                MusicSourceSwitcher(
                    context = application,
                    resumer = MediaResumer(port = PlatformResumePort(application), forget = lastPlayed::forget),
                    openApp = { packageName -> apps.launcherComponentFor(packageName)?.let(apps::launch) ?: false },
                )
            MusicSourcesViewModel(
                settings = preferences.settings,
                lastUsed = lastPlayed.track.map { it?.packageName },
                installedApps = apps.packageChanges.onStart { emit(Unit) }.map { apps.queryApps() },
                ownPackage = application.packageName,
                setPicked = preferences::setPicked,
                setShowPanel = preferences::setShowPanel,
                play = switcher::play,
            )
        }
    }
