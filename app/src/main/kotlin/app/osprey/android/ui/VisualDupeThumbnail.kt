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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.android.filesystem.AndroidFileHandle
import app.osprey.media.MediaMetadataReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BYTES_PER_KB = 1024
private const val THUMBNAIL_WIDTH_DP = 120

@Suppress("MagicNumber") // Hex color literals, not measurements.
private val KEEP_COLOR = Color(0xFF22AA66)

@Suppress("MagicNumber")
private val UNCHECKED_TOGGLE_BG = Color(0x80141414)

/** One card in a group's grid: thumbnail, keep chip, toggle, name/size -- same shape as desktop's (#12). */
@Composable
fun VisualDupeThumbnail(
    mediaMetadataReader: MediaMetadataReader,
    member: VisualDupeMember,
    kept: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val context = LocalContext.current
    val isImage = splitStemAndSuffix(member.name).second.lowercase() in imageExtensions
    var frame by remember(member.handle) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(member.handle) {
        frame = withContext(Dispatchers.IO) { mediaMetadataReader.decodeFrame(member.handle)?.toImageBitmap() }
    }

    Column(
        modifier = Modifier
            .width(THUMBNAIL_WIDTH_DP.dp)
            .border(2.dp, if (kept) KEEP_COLOR else Color.Transparent, RoundedCornerShape(8.dp))
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
            frame?.let {
                Image(
                    it,
                    contentDescription = member.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (!isImage) {
                IconButton(onClick = { openInSystemPlayer(context, member) }) {
                    Text("▶", color = Color.White, fontSize = 28.sp)
                }
            }
            if (kept) KeepChip()
            KeepToggle(kept, onToggle)
        }
        Column(Modifier.padding(6.dp, 6.dp, 6.dp, 8.dp)) {
            Text(member.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${member.sizeBytes / BYTES_PER_KB} KB", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun BoxScope.KeepChip() {
    Surface(
        color = KEEP_COLOR,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
    ) {
        Text(
            "KEEP",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BoxScope.KeepToggle(kept: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(if (kept) KEEP_COLOR else UNCHECKED_TOGGLE_BG)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (kept) Text("✓", color = Color.White, fontSize = 13.sp)
    }
}

private fun openInSystemPlayer(context: android.content.Context, member: VisualDupeMember) {
    val handle = member.handle
    if (handle is AndroidFileHandle) {
        val uri = handle.documentFile.uri
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, handle.documentFile.type ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
    }
}
