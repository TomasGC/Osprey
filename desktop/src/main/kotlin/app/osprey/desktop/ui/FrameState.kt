package app.osprey.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import app.osprey.filesystem.FileHandle
import app.osprey.media.PixelBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What decoding a photo has produced so far: nothing yet, a picture, or nothing to show. */
sealed interface FrameState {
    data object Loading : FrameState

    class Ready(val bitmap: ImageBitmap) : FrameState

    data object Unavailable : FrameState
}

const val PREVIEW_UNAVAILABLE = "Preview unavailable"

/** Decodes with [decode] off the UI thread whenever [handle] changes: Loading, then the picture or Unavailable. */
@Composable
fun rememberFrameState(handle: FileHandle, decode: () -> PixelBuffer?): FrameState {
    var state by remember(handle) { mutableStateOf<FrameState>(FrameState.Loading) }
    LaunchedEffect(handle) {
        state = withContext(Dispatchers.IO) { decode()?.toImageBitmap() }
            ?.let { FrameState.Ready(it) } ?: FrameState.Unavailable
    }
    return state
}

/** Said where a picture would be when the photo cannot be decoded (ffmpeg missing, damaged file). */
@Composable
fun PreviewUnavailable(color: Color = Color.Gray) {
    Text(PREVIEW_UNAVAILABLE, color = color, fontSize = 11.sp, textAlign = TextAlign.Center)
}
