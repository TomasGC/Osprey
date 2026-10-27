package app.osprey.android.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.formatSize
import app.osprey.media.CARD_DECODE_MAX_SIDE
import app.osprey.media.MediaMetadataReader
import app.osprey.viewer.ViewTurns

private const val THUMBNAIL_WIDTH_DP = 120
private const val DEGREES_PER_TURN = 90f

@Suppress("MagicNumber") // Hex color literals, not measurements.
val CHECKED_COLOR = Color(0xFF22AA66)

@Suppress("MagicNumber")
private val UNCHECKED_TOGGLE_BG = Color(0x80141414)

/**
 * One file as a card: thumbnail (a video opens in the system player), an optional [badge] chip while
 * [checked], a round toggle, name and size. Shared by Visual Dupes (checked = keep) and the Trash
 * (checked = selected). [showToggle] is off where a card only shows a file.
 */
@Composable
fun MediaCard(
    mediaMetadataReader: MediaMetadataReader,
    viewTurns: ViewTurns,
    handle: FileHandle,
    name: String,
    sizeBytes: Long,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    badge: String? = null,
    showToggle: Boolean = true,
) {
    val context = LocalContext.current
    val isImage = splitStemAndSuffix(name).second.lowercase() in imageExtensions
    val frame = rememberFrameState(handle) { mediaMetadataReader.decodeFrame(handle, CARD_DECODE_MAX_SIDE) }
    val quarterTurns = viewTurns.turns.collectAsState().value[handle] ?: 0

    Column(
        modifier = Modifier
            .width(THUMBNAIL_WIDTH_DP.dp)
            .border(2.dp, if (checked) CHECKED_COLOR else Color.Transparent, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.Black)
                .clickable(enabled = isImage, onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            when (frame) {
                is FrameState.Ready -> Image(
                    frame.bitmap,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize().rotate(DEGREES_PER_TURN * quarterTurns),
                    contentScale = ContentScale.Crop,
                )
                FrameState.Unavailable -> if (isImage) PreviewUnavailable()
                FrameState.Loading -> Unit
            }
            if (!isImage) {
                IconButton(onClick = { openInSystemPlayer(context, handle) }) {
                    Text("▶", color = Color.White, fontSize = 28.sp)
                }
            }
            if (checked && badge != null) BadgeChip(badge)
            if (showToggle) CheckToggle(checked, onToggle)
        }
        Column(Modifier.padding(6.dp, 6.dp, 6.dp, 8.dp)) {
            Text(name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSize(sizeBytes), fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun BoxScope.BadgeChip(text: String) {
    Surface(
        color = CHECKED_COLOR,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BoxScope.CheckToggle(checked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(if (checked) CHECKED_COLOR else UNCHECKED_TOGGLE_BG)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, fontSize = 13.sp)
    }
}

private fun openInSystemPlayer(context: android.content.Context, handle: FileHandle) {
    if (handle is AndroidFileHandle) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(handle.documentFile.uri, handle.documentFile.type ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }
}
