package app.osprey.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.osprey.filesystem.FileHandle
import app.osprey.media.MediaMetadataReader
import app.osprey.media.PhotoSize
import app.osprey.viewer.DetailTile
import app.osprey.viewer.FIT_ZOOM
import app.osprey.viewer.ViewTransform
import app.osprey.viewer.ViewTurns
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("MagicNumber") // Hex color literal, not a measurement.
private val SCRIM = Color(0xE6000000)

/**
 * A phone heap cannot hold a 40 MP photo twice (pixels, then bitmap): the lightbox decodes the whole photo at screen
 * size (#49) and the zoomed part of it, at real pixels, in tiles (#60).
 */
private const val PREVIEW_DECODE_MAX_SIDE = 2048

private const val DEGREES_PER_TURN = 90f
private const val PERCENT = 100

/** The size a reader reported for a photo, or none: told apart from "not read yet", which is no holder at all. */
private class PhotoDimensions(val size: PhotoSize?)

/**
 * Fullscreen image preview with its own checkbox ([checkLabel]), kept in sync with the grid. The photo can be pinched,
 * dragged, double-tapped and turned (#51); a turn is only in memory ([viewTurns]) until Save turn writes it into a
 * JPEG's file (#61).
 * The whole photo is decoded at [PREVIEW_DECODE_MAX_SIDE]; zoomed in, the part in view is decoded again at its real
 * pixels, tile by tile, over that base (#60). A tap beside the photo closes it: there is no Escape key on a phone.
 */
@Composable
fun MediaPreview(
    mediaMetadataReader: MediaMetadataReader,
    viewTurns: ViewTurns,
    handle: FileHandle,
    name: String,
    checked: Boolean,
    checkLabel: String,
    onCheckedChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val revision = viewTurns.revisions.collectAsState().value[handle] ?: 0
    val pending = viewTurns.turns.collectAsState().value[handle] ?: 0
    val frame = rememberFrameState(handle, revision) {
        mediaMetadataReader.decodeFrame(handle, PREVIEW_DECODE_MAX_SIDE)
    }
    var dimensions by remember(handle, revision) { mutableStateOf<PhotoDimensions?>(null) }
    LaunchedEffect(handle, revision) {
        dimensions = PhotoDimensions(withContext(Dispatchers.IO) { mediaMetadataReader.imageSize(handle) })
    }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var stored by remember(handle, revision) { mutableStateOf<ViewTransform?>(null) }
    val bitmap = (frame as? FrameState.Ready)?.bitmap
    // The photo's own size, not the base picture's: zoom and tiles are measured on the full photo. A reader that
    // cannot tell it leaves the base picture's size, and no detail.
    val photo = bitmap?.let { base -> dimensions?.let { it.size ?: PhotoSize(base.width, base.height) } }
    val transform = photo?.let { transformFor(it, viewSize, stored, pending) }
    val latest by rememberUpdatedState(transform)
    val tiles = rememberDetailTiles(mediaMetadataReader, handle, revision, transform, PREVIEW_DECODE_MAX_SIDE)
    val save = rememberTurnSaveControls(viewTurns, handle, name, pending)

    Column(
        modifier = Modifier.fillMaxSize().background(SCRIM),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .onSizeChanged { viewSize = it }
                .viewerGestures({ latest }, { stored = it }, onClose)
                .testTag("viewer"),
            contentAlignment = Alignment.Center,
        ) {
            when (frame) {
                is FrameState.Ready -> photo?.let { Photo(frame.bitmap, it, name, transform, tiles) }
                FrameState.Unavailable -> PreviewUnavailable(Color.White)
                FrameState.Loading -> Unit
            }
        }
        ViewerControls(
            zoom = transform?.zoom ?: FIT_ZOOM,
            onTurn = { quarters ->
                viewTurns.turn(handle, quarters)
                stored = if (quarters > 0) latest?.rotateRight() else latest?.rotateLeft()
            },
            save = save,
            checked = checked,
            checkLabel = checkLabel,
            onCheckedChange = onCheckedChange,
            name = name,
            onClose = onClose,
        )
    }
}

/** Double tap, pinch (around the fingers' centre) and drag change the transform via [update]; a tap beside closes. */
private fun Modifier.viewerGestures(
    current: () -> ViewTransform?,
    update: (ViewTransform?) -> Unit,
    onClose: () -> Unit,
): Modifier = this
    .pointerInput(Unit) {
        detectTapGestures(
            onDoubleTap = { update(current()?.doubleTap(it.x, it.y)) },
            onTap = { if (current()?.covers(it.x, it.y) != true) onClose() },
        )
    }
    .pointerInput(Unit) {
        detectTransformGestures { centroid, pan, zoom, _ ->
            update(current()?.zoomAt(centroid.x, centroid.y, zoom)?.panBy(pan.x, pan.y))
        }
    }

@Composable
private fun ViewerControls(
    zoom: Float,
    onTurn: (Int) -> Unit,
    save: TurnSaveControls,
    checked: Boolean,
    checkLabel: String,
    onCheckedChange: (Boolean) -> Unit,
    name: String,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onTurn(-1) },
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.semantics { contentDescription = "Rotate left" },
            ) { Text("↺") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onTurn(1) },
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.semantics { contentDescription = "Rotate right" },
            ) { Text("↻") }
            Spacer(Modifier.width(12.dp))
            Text("${(zoom * PERCENT).roundToInt()}%", color = Color.White)
            Spacer(Modifier.weight(1f))
            Button(onClick = onClose) { Text("Close") }
        }
        save.note?.let { Text(it, color = Color.White) }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(checkLabel, color = Color.White)
            Spacer(Modifier.weight(1f))
            Button(
                onClick = save.onSave,
                enabled = save.canSave,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.semantics { contentDescription = "Save turn" },
            ) { Text("Save turn") }
        }
        Text(name, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The transform to show [photo] with: the stored one resized to the view, or a fresh one at fit and [turns]. */
private fun transformFor(photo: PhotoSize, viewSize: IntSize, stored: ViewTransform?, turns: Int): ViewTransform? {
    if (viewSize == IntSize.Zero) return null
    val width = viewSize.width.toFloat()
    val height = viewSize.height.toFloat()
    return stored?.resizedTo(width, height)
        ?: ViewTransform(width, height, photo.width.toFloat(), photo.height.toFloat(), quarterTurns = turns)
}

/**
 * The photo at the transform's zoom, pan and turn: the base picture and the [tiles] laid out at the full photo's fit,
 * then scaled and moved together by the layer.
 */
@Composable
private fun Photo(
    bitmap: ImageBitmap,
    photo: PhotoSize,
    name: String,
    transform: ViewTransform?,
    tiles: Map<DetailTile, ImageBitmap>,
) {
    Box(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            if (transform != null) {
                val ownFit = min(size.width / photo.width, size.height / photo.height)
                scaleX = transform.scale / ownFit
                scaleY = scaleX
                rotationZ = DEGREES_PER_TURN * transform.quarterTurns
                translationX = transform.panX
                translationY = transform.panY
            }
        },
    ) {
        Image(bitmap, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        DetailTilesCanvas(photo, tiles)
    }
}
