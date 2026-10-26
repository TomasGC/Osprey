package app.osprey.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaMetadataReader

@Suppress("MagicNumber") // Hex color literal, not a measurement.
private val SCRIM = Color(0xE6000000)
private const val PREVIEW_WIDTH_FRACTION = 0.95f

/** A phone heap cannot hold a 40 MP photo twice (pixels, then bitmap): the lightbox decodes at screen size (#49). */
private const val PREVIEW_DECODE_MAX_SIDE = 2048

@Suppress("MagicNumber")
private val PREVIEW_MAX_HEIGHT = 480.dp

/**
 * Fullscreen image preview with its own checkbox ([checkLabel]), kept in sync with the grid. Tapping the
 * scrim closes it: there is no Escape key on a phone.
 */
@Composable
fun MediaPreview(
    mediaMetadataReader: MediaMetadataReader,
    handle: FileHandle,
    name: String,
    checked: Boolean,
    checkLabel: String,
    onCheckedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val frame = rememberFrameState(handle) { mediaMetadataReader.decodeFrame(handle, PREVIEW_DECODE_MAX_SIDE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClose,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (frame) {
            is FrameState.Ready -> Image(
                frame.bitmap,
                contentDescription = name,
                modifier = Modifier.fillMaxWidth(PREVIEW_WIDTH_FRACTION).heightIn(max = PREVIEW_MAX_HEIGHT),
                contentScale = ContentScale.Fit,
            )
            FrameState.Unavailable -> PreviewUnavailable(Color.White)
            FrameState.Loading -> Unit
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(checkLabel, color = Color.White)
            Spacer(Modifier.width(14.dp))
            Button(onClick = onClose) { Text("Close") }
        }
        Spacer(Modifier.height(4.dp))
        Text(name, color = Color.Gray)
    }
}
