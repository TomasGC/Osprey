package app.osprey.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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
import app.osprey.viewer.FIT_ZOOM
import app.osprey.viewer.ViewTransform
import app.osprey.viewer.ViewTurns
import app.osprey.viewer.zoomByWheel
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("MagicNumber") // Hex color literal, not a measurement.
private val SCRIM = Color(0xE6000000)

private const val DEGREES_PER_TURN = 90f
private const val PERCENT = 100

/**
 * Fullscreen image preview with its own checkbox ([checkLabel]), kept in sync with the grid. The photo is shown at
 * full resolution and can be zoomed (mouse wheel at the cursor, double click), dragged and turned (#51); a turn is
 * only in memory ([viewTurns]) until Save turn writes it into a JPEG or PNG file (#61, #70). Escape, Close and a
 * click beside the photo close it.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    val frame = rememberFrameState(handle, revision) { mediaMetadataReader.decodeFrame(handle) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var stored by remember(handle, revision) { mutableStateOf<ViewTransform?>(null) }
    val bitmap = (frame as? FrameState.Ready)?.bitmap
    val transform = bitmap?.let { transformFor(it, viewSize, stored, pending) }
    val latest by rememberUpdatedState(transform)
    val save = rememberTurnSaveControls(viewTurns, handle, name, pending)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent {
                val isEscape = it.type == KeyEventType.KeyDown && it.key == Key.Escape
                if (isEscape) onClose()
                isEscape
            },
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
                is FrameState.Ready -> Photo(frame.bitmap, name, transform)
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

/** Wheel at the cursor, drag and double click change the transform via [update]; a click beside the photo closes. */
@OptIn(ExperimentalComposeUiApi::class)
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
        detectDragGestures { change, drag ->
            change.consume()
            update(current()?.panBy(drag.x, drag.y))
        }
    }
    .onPointerEvent(PointerEventType.Scroll) { event ->
        val change = event.changes.first()
        update(current()?.zoomByWheel(change.position.x, change.position.y, change.scrollDelta.y))
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
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { onTurn(-1) },
                modifier = Modifier.semantics { contentDescription = "Rotate left" },
            ) { Text("↺") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onTurn(1) },
                modifier = Modifier.semantics { contentDescription = "Rotate right" },
            ) { Text("↻") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = save.onSave,
                enabled = save.canSave,
                modifier = Modifier.semantics { contentDescription = "Save turn" },
            ) { Text("Save turn") }
            Spacer(Modifier.width(14.dp))
            Text("${(zoom * PERCENT).roundToInt()}%", color = Color.White)
            Spacer(Modifier.width(14.dp))
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(checkLabel, color = Color.White)
            Spacer(Modifier.width(14.dp))
            Text(
                name,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(14.dp))
            Button(onClick = onClose) { Text("Close") }
        }
        save.note?.let { Text(it, color = Color.Gray) }
    }
}

/** The transform to show [bitmap] with: the stored one resized to the view, or a fresh one at fit and [turns]. */
private fun transformFor(bitmap: ImageBitmap, viewSize: IntSize, stored: ViewTransform?, turns: Int): ViewTransform? {
    if (viewSize == IntSize.Zero) return null
    val width = viewSize.width.toFloat()
    val height = viewSize.height.toFloat()
    return stored?.resizedTo(width, height)
        ?: ViewTransform(width, height, bitmap.width.toFloat(), bitmap.height.toFloat(), quarterTurns = turns)
}

/** The photo at the transform's zoom, pan and turn: laid out at its own fit, then scaled and moved by the layer. */
@Composable
private fun Photo(bitmap: ImageBitmap, name: String, transform: ViewTransform?) {
    Image(
        bitmap,
        contentDescription = name,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            if (transform != null) {
                val ownFit = min(size.width / bitmap.width, size.height / bitmap.height)
                scaleX = transform.scale / ownFit
                scaleY = scaleX
                rotationZ = DEGREES_PER_TURN * transform.quarterTurns
                translationX = transform.panX
                translationY = transform.panY
            }
        },
        contentScale = ContentScale.Fit,
    )
}
