package app.osprey.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import app.osprey.android.AndroidAppState
import app.osprey.media.MediaMetadataReader
import app.osprey.viewer.ViewTurns

/**
 * The Reorg dialog (#91) over the whole screen while the review is open: Continue and Cancel go to the controller.
 */
@Composable
fun ReorgReviewHost(state: AndroidAppState) {
    val root = state.libraryRoot ?: return
    DuplicateReviewDialog(
        review = state.reorg.review,
        mediaMetadataReader = state.mediaMetadataReader,
        viewTurns = state.viewTurns,
        onContinue = { state.reorg.onContinue(root) },
        onCancel = { state.reorg.onCancel() },
    )
}

/**
 * "Which files do you want to keep?" (#91): the pairs of [review], a Move input file next to its identical library
 * file, each side ticked to keep. Draws nothing while the review is closed. It fills the screen; Back closes the
 * preview if one is open, else cancels. The list is lazy, and the Details view decodes nothing until a file name is
 * clicked.
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
    BackHandler { if (view.previewing != null) review.preview(null) else onCancel() }
    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} }) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(8.dp)) {
                ReviewTitle(view, review)
                Spacer(Modifier.height(4.dp))
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
    Column {
        Text("Which files do you want to keep?", fontSize = 18.sp)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("View:", fontSize = 13.sp)
            ModeButton("Miniatures", view.mode == DuplicateViewMode.MINIATURES) {
                review.setMode(DuplicateViewMode.MINIATURES)
            }
            ModeButton("Details", view.mode == DuplicateViewMode.DETAILS) {
                review.setMode(DuplicateViewMode.DETAILS)
            }
        }
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
private fun RowScope.HeaderCell(text: String, description: String, kept: Boolean, onToggle: () -> Unit) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = kept,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = description },
        )
        Text(text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReviewFooter(text: String, onContinue: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text, fontSize = 13.sp)
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onContinue) { Text("Continue") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
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
