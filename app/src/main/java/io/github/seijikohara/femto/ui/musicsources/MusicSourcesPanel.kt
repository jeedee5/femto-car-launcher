package io.github.seijikohara.femto.ui.musicsources

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import dev.chrisbanes.haze.HazeState
import io.github.seijikohara.femto.R
import io.github.seijikohara.femto.ui.home.components.GlassConfig
import io.github.seijikohara.femto.ui.home.components.PanelIconButton
import io.github.seijikohara.femto.ui.home.components.glassChrome
import io.github.seijikohara.femto.ui.theme.FemtoDimens
import io.github.seijikohara.femto.ui.theme.cardCtaHint

private val SourceIconSize = 44.dp
private val DialogIconSize = 36.dp
private val LastUsedDotSize = 6.dp

/**
 * The dashboard's music sources panel: the picked player apps (plus the one that played last) as one-tap tiles, and
 * an edit tile that opens the picker. [playingPackage] is the app the music card shows playing, highlighted here.
 * Renders nothing while the panel is switched off.
 */
@Composable
internal fun MusicSourcesPanel(
    hazeState: HazeState,
    glassConfig: GlassConfig,
    playingPackage: String?,
    modifier: Modifier = Modifier,
) {
    val viewModel: MusicSourcesViewModel = viewModel(factory = MusicSourcesViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    if (uiState.showPanel) {
        MusicSourcesCard(
            uiState = uiState,
            playingPackage = playingPackage,
            onPlay = { viewModel.onAction(MusicSourcesAction.Play(it)) },
            onEdit = { picking = true },
            hazeState = hazeState,
            glassConfig = glassConfig,
            modifier = modifier,
        )
    }
    if (picking) {
        MusicSourcesPickerDialog(
            installed = uiState.installed,
            picked = uiState.picked,
            onToggle = { packageName, picked -> viewModel.onAction(MusicSourcesAction.SetPicked(packageName, picked)) },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun MusicSourcesCard(
    uiState: MusicSourcesUiState,
    playingPackage: String?,
    onPlay: (String) -> Unit,
    onEdit: () -> Unit,
    hazeState: HazeState,
    glassConfig: GlassConfig,
    modifier: Modifier = Modifier,
) = Surface(
    modifier = modifier.glassChrome(MaterialTheme.shapes.large, hazeState, glassConfig),
    shape = MaterialTheme.shapes.large,
    color = Color.Transparent,
    contentColor = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = FemtoDimens.CardPaddingCompact, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (uiState.tiles.isEmpty()) {
            Text(
                text = stringResource(R.string.music_sources_empty),
                style = MaterialTheme.typography.cardCtaHint(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onEdit).padding(vertical = 8.dp),
            )
        } else {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                uiState.tiles.forEach { app ->
                    SourceTile(
                        app = app,
                        playing = app.packageName == playingPackage,
                        lastUsed = app.packageName == uiState.lastUsed,
                        starting = app.packageName == uiState.pending,
                        enabled = uiState.pending == null,
                        onClick = { onPlay(app.packageName) },
                    )
                }
            }
        }
        PanelIconButton(
            icon = if (uiState.picked.isEmpty()) Lucide.Plus else Lucide.Pencil,
            description = stringResource(R.string.music_sources_edit),
            onClick = onEdit,
        )
    }
}

@Composable
private fun SourceTile(
    app: MusicSourceApp,
    playing: Boolean,
    lastUsed: Boolean,
    starting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.music_sources_play, app.label)
    Box(
        modifier =
            Modifier
                .size(FemtoDimens.MinTouchTarget)
                .clip(MaterialTheme.shapes.medium)
                .background(if (playing) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = null,
            // Dimmed while this source is starting, as the tap's only feedback until it plays.
            modifier = Modifier.size(SourceIconSize).alpha(if (starting) 0.4f else 1f),
        )
        if (lastUsed && !playing) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(LastUsedDotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/** Checkbox list of every launchable app; checking appends the app to the panel, unchecking removes it. */
@Composable
private fun MusicSourcesPickerDialog(
    installed: List<MusicSourceApp>,
    picked: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.music_sources_picker_title)) },
    text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            installed.forEach { app ->
                val checked = app.packageName in picked
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = FemtoDimens.MinTouchTarget)
                            .toggleable(
                                value = checked,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(app.packageName, it) },
                            ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(DialogIconSize))
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    },
    confirmButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_dialog_close)) }
    },
)

/** The Settings > Panels switch for the panel; it reads and writes the same store as the dashboard. */
@Composable
internal fun MusicSourcesSwitch(
    content: @Composable (checked: Boolean, onCheckedChange: (Boolean) -> Unit) -> Unit,
) {
    val viewModel: MusicSourcesViewModel = viewModel(factory = MusicSourcesViewModelFactory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    content(uiState.showPanel) { viewModel.onAction(MusicSourcesAction.SetShowPanel(it)) }
}
