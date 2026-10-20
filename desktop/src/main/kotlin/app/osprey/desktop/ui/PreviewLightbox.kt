package app.osprey.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.desktop.VisualDupeGroupUi
import app.osprey.media.MediaMetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("MagicNumber") // Hex color literal, not a measurement.
private val SCRIM = Color(0xE6000000)
private const val PREVIEW_WIDTH_FRACTION = 0.8f

@Suppress("MagicNumber")
private val PREVIEW_MAX_HEIGHT = 560.dp

/** Fullscreen image preview with its own keep/dup toggle, kept in sync with the grid. Escape closes. */
@Composable
fun PreviewLightbox(
    group: VisualDupeGroupUi,
    member: VisualDupeMember,
    mediaMetadataReader: MediaMetadataReader,
    onClose: () -> Unit,
) {
    var frame by remember(member.handle) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(member.handle) {
        frame = withContext(Dispatchers.IO) { mediaMetadataReader.decodeFrame(member.handle)?.toImageBitmap() }
    }
    val kept = group.keep[member.name] == true
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent {
                if (it.key == Key.Escape) {
                    onClose()
                    true
                } else {
                    false
                }
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        frame?.let {
            Image(
                it,
                contentDescription = member.name,
                modifier = Modifier.fillMaxWidth(PREVIEW_WIDTH_FRACTION).heightIn(max = PREVIEW_MAX_HEIGHT),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = kept, onCheckedChange = { group.keep[member.name] = it })
            Text("Keep this one", color = Color.White)
            Spacer(Modifier.width(14.dp))
            Text(member.name, color = Color.Gray)
            Spacer(Modifier.width(14.dp))
            Button(onClick = onClose) { Text("Close") }
        }
    }
}
