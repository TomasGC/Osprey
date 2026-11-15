package app.osprey.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.actions.reorg.DuplicateReview
import app.osprey.actions.reorg.DuplicateReviewView
import app.osprey.actions.reorg.DuplicateSide
import app.osprey.actions.reorg.DuplicateViewMode
import app.osprey.media.MediaMetadataReader
import app.osprey.viewer.ViewTurns

@Suppress("MagicNumber") // Hex color literal, not a measurement.
private val DIALOG_SCRIM = Color(0xCC000000)

private val DIALOG_MAX_WIDTH = 900.dp
private const val DIALOG_SHARE = 0.92f

/**
 * "Which files do you want to keep?" (#91): the pairs of [review], a Move input file next to its identical library
 * file, each side ticked to keep. Draws nothing while the review is closed. The list is lazy, and the Details view
 * decodes nothing until a file name is clicked.
 */
@Composable
fun DuplicateReviewDialog(
    review: DuplicateReview,
    mediaMetadataReader: MediaMetadataReader,
    viewTurns: ViewTurns,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    val view = review.view.collectAsState().value ?: return
    Box(
        modifier = Modifier.fillMaxSize().background(DIALOG_SCRIM).pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = DIALOG_MAX_WIDTH).fillMaxWidth(DIALOG_SHARE).fillMaxHeight(DIALOG_SHARE),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ReviewTitle(view, review)
                Spacer(Modifier.height(8.dp))
                ColumnHeaders(view, review)
                ReviewList(view, review, mediaMetadataReader, viewTurns, Modifier.weight(1f))
                ReviewFooter(view.footer, onContinue, onCancel)
            }
        }
        view.previewing?.let { ReviewPreview(view, review, it, mediaMetadataReader, viewTurns) }
    }
}

@Composable
private fun ReviewTitle(view: DuplicateReviewView, review: DuplicateReview) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Which files do you want to keep?", fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text("View:", fontSize = 13.sp)
        ModeButton("Miniatures", view.mode == DuplicateViewMode.MINIATURES) {
            review.setMode(DuplicateViewMode.MINIATURES)
        }
        ModeButton("Details", view.mode == DuplicateViewMode.DETAILS) { review.setMode(DuplicateViewMode.DETAILS) }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun ColumnHeaders(view: DuplicateReviewView, review: DuplicateReview) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderCell(view.inputHeader, "Keep all from the input", view.inputColumnKept, review::toggleInputColumn)
        HeaderCell(view.libraryHeader, "Keep all in the library", view.libraryColumnKept, review::toggleLibraryColumn)
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    description: String,
    kept: Boolean,
    onToggle: () -> Unit,
) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = kept,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = description },
        )
        Text(text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReviewFooter(text: String, onContinue: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Button(onClick = onContinue) { Text("Continue") }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** The zoomable preview of one file, over the dialog; its Keep box is that file's tick. */
@Composable
private fun ReviewPreview(
    view: DuplicateReviewView,
    review: DuplicateReview,
    side: DuplicateSide,
    mediaMetadataReader: MediaMetadataReader,
    viewTurns: ViewTurns,
) {
    val index = view.rows.indexOfFirst { it.duplicate.input == side || it.duplicate.library == side }
    val row = view.rows[index]
    val isInput = row.duplicate.input == side
    MediaPreview(
        mediaMetadataReader = mediaMetadataReader,
        viewTurns = viewTurns,
        handle = side.handle,
        name = side.name,
        checked = if (isInput) row.keepInput else row.keepLibrary,
        checkLabel = "Keep",
        onCheckedChange = { if (isInput) review.toggleInput(index) else review.toggleLibrary(index) },
        onClose = { review.preview(null) },
    )
}
