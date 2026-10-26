package app.osprey.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.osprey.actions.visual.VisualDupeMember
import app.osprey.actions.visual.burstId

/**
 * One group's UI-facing, mutable presentation state: member order, each member's keep/dup
 * toggle, and whether this group has already been processed. Matches the POC's
 * `_scan_visual_thread` transform (burst members first and all pre-checked for a burst set,
 * otherwise just the heaviest member) -- this reordering is presentation, not planning, so it
 * lives here rather than in #8's `VisualDupePlanner`.
 */
class VisualDupeGroupUi(
    val index: Int,
    val isBurst: Boolean,
    val members: List<VisualDupeMember>,
    initialKeep: Set<String>,
) {
    val keep = mutableStateMapOf<String, Boolean>().apply {
        members.forEach { put(it.name, it.name in initialKeep) }
    }
    var done by mutableStateOf(false)

    /** True while this group's files are being moved to the Trash by a job. */
    var moving by mutableStateOf(false)
    var status by mutableStateOf("")
}

fun toUiGroups(groups: List<List<VisualDupeMember>>): List<VisualDupeGroupUi> =
    groups.mapIndexed { i, group -> toUiGroup(i + 1, group) }

private fun toUiGroup(index: Int, group: List<VisualDupeMember>): VisualDupeGroupUi {
    val burstMembers = group.filter { burstId(it.name) != null }
    return if (burstMembers.size >= 2) {
        val ordered = burstMembers.sortedByDescending { it.sizeBytes } + group.filter { burstId(it.name) == null }
        VisualDupeGroupUi(index, isBurst = true, members = ordered, initialKeep = burstMembers.map { it.name }.toSet())
    } else {
        VisualDupeGroupUi(index, isBurst = false, members = group, initialKeep = setOf(group.first().name))
    }
}
