package app.osprey.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.osprey.android.VisualDupeGroupUi

private const val BYTES_PER_MB = 1024.0 * 1024.0

@Suppress("MagicNumber") // Hex color literal, not a measurement.
private val RECLAIM_COLOR = Color(0xFF7CFC7C)

/** Groups / candidate-dupes / reclaimable-size chips -- same shape as desktop's (#12). */
@Composable
fun VisualDupeStatsRow(groups: List<VisualDupeGroupUi>) {
    val extraCount = groups.sumOf { group -> group.members.count { group.keep[it.name] != true } }
    val reclaimBytes = groups.sumOf { group ->
        group.members.filter { group.keep[it.name] != true }.sumOf { it.sizeBytes }
    }
    val reclaimMb = reclaimBytes / BYTES_PER_MB

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatChip("Groups", groups.size.toString())
        StatChip("Candidate dupes", extraCount.toString())
        StatChip("Reclaimable", "~%.1f MB".format(reclaimMb), RECLAIM_COLOR)
    }
}

@Composable
private fun StatChip(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
            Text(label.uppercase(), fontSize = 10.sp)
        }
    }
}
