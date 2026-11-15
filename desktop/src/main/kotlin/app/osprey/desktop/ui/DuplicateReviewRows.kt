package app.osprey.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.actions.reorg.DuplicateReview
import app.osprey.actions.reorg.DuplicateReviewView
import app.osprey.actions.reorg.DuplicateRow
import app.osprey.actions.reorg.DuplicateSide
import app.osprey.actions.reorg.DuplicateViewMode
import app.osprey.filesystem.formatSize
import app.osprey.media.MediaMetadataReader
import app.osprey.viewer.ViewTurns
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NO_DATE = "No date"
private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.getDefault())

@Composable
internal fun ReviewList(
    view: DuplicateReviewView,
    review: DuplicateReview,
    mediaMetadataReader: MediaMetadataReader,
    viewTurns: ViewTurns,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(view.rows) { index, row ->
            if (view.mode == DuplicateViewMode.MINIATURES) {
                MiniatureRow(index, row, review, mediaMetadataReader, viewTurns)
            } else {
                DetailsRow(index, row, review)
            }
        }
    }
}

@Composable
private fun MiniatureRow(
    index: Int,
    row: DuplicateRow,
    review: DuplicateReview,
    mediaMetadataReader: MediaMetadataReader,
    viewTurns: ViewTurns,
) {
    Column {
        Text(row.duplicate.input.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(modifier = Modifier.fillMaxWidth()) {
            val input = row.duplicate.input
            val library = row.duplicate.library
            MiniatureCell(input, row.keepInput, { review.toggleInput(index) }, review, mediaMetadataReader, viewTurns)
            MiniatureCell(
                library, row.keepLibrary, { review.toggleLibrary(index) }, review, mediaMetadataReader, viewTurns,
            )
        }
    }
}

@Composable
private fun RowScope.MiniatureCell(
    side: DuplicateSide,
    kept: Boolean,
    onToggle: () -> Unit,
    review: DuplicateReview,
    mediaMetadataReader: MediaMetadataReader,
    viewTurns: ViewTurns,
) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
        KeepBox(side, kept, onToggle)
        Column {
            Box(modifier = Modifier.semantics { contentDescription = "Open ${side.label}" }) {
                MediaCard(
                    mediaMetadataReader, viewTurns, side.handle, side.name, side.sizeBytes,
                    checked = false, onToggle = {}, onOpen = { review.preview(side) },
                    showToggle = false, showCaption = false,
                )
            }
            Text(describe(side), fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun DetailsRow(index: Int, row: DuplicateRow, review: DuplicateReview) {
    Row(modifier = Modifier.fillMaxWidth()) {
        DetailsCell(row.duplicate.input, row.keepInput, { review.toggleInput(index) }, review)
        DetailsCell(row.duplicate.library, row.keepLibrary, { review.toggleLibrary(index) }, review)
    }
}

@Composable
private fun RowScope.DetailsCell(
    side: DuplicateSide,
    kept: Boolean,
    onToggle: () -> Unit,
    review: DuplicateReview,
) {
    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        KeepBox(side, kept, onToggle)
        Column {
            Text(
                side.name,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = TextDecoration.Underline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { review.preview(side) }
                    .semantics { contentDescription = "Open ${side.label}" },
            )
            Text(describe(side), fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun KeepBox(side: DuplicateSide, kept: Boolean, onToggle: () -> Unit) {
    Checkbox(
        checked = kept,
        onCheckedChange = { onToggle() },
        modifier = Modifier.semantics { contentDescription = "Keep ${side.label}" },
    )
}

private fun describe(side: DuplicateSide): String =
    "${side.date?.format(DATE_FORMAT) ?: NO_DATE}  ${formatSize(side.sizeBytes)}"
