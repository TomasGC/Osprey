package app.osprey.actions.visual

import app.osprey.actions.imageExtensions
import app.osprey.actions.splitStemAndSuffix
import app.osprey.actions.videoExtensions
import app.osprey.filesystem.FileEntry
import app.osprey.filesystem.FileHandle
import app.osprey.filesystem.FileSystemRepository
import app.osprey.jobs.JobControl
import app.osprey.jobs.NoJobControl
import app.osprey.jobs.forEachUntilStopped
import app.osprey.media.MediaMetadataReader

/**
 * Lower = stricter; 0 = pixel-identical-after-resize. The POC's own `VISUAL_HAMMING_THRESHOLD`
 * was 6, but that number isn't portable as-is: [phash] is a hand-rolled, differently-scaled hash
 * (see its own doc), not a port of Python's `imagehash.phash`. Measured directly against this
 * hash on real photos (issue #26): a real recompression or a WhatsApp-style downscale-and-
 * recompress of the same capture lands at distance 0, while three distinct-but-similar shots of
 * the same scene, seconds apart, land at 4-6 -- so 6 was matching both. 2 keeps a safety margin
 * above the measured dupe case and below the measured distinct-shot case.
 */
const val VISUAL_HAMMING_THRESHOLD = 2

/** Longest side the planner asks the reader for: [phash] samples a 32x32 grid, 128 oversamples it 4x. */
const val HASH_DECODE_MAX_SIDE = 128

private const val BYTES_PER_KB = 1024

/**
 * Finds same-photo-different-bytes duplicates (recompression, re-export, cross-date forwards)
 * among the images/videos directly inside `root`, that byte-hash dedup (#5) can never catch.
 * A straight re-platform of the POC's `plan_visual_dupes`/`describe_visual_dupes`: perceptual
 * hash via [phash], clustered with [UnionFind] at Hamming distance <= [VISUAL_HAMMING_THRESHOLD].
 *
 * Burst-mode frames ([burstId]) are visually near-identical but are N distinct shots, not
 * duplicates of each other: two frames sharing a burst id are never unioned directly. That alone
 * isn't sufficient — frames can still end up in the same cluster via a bridging third file — so
 * [describe] additionally reports a cluster containing >= 2 distinct burst members as "keep all
 * N frames" at the cluster level, not the pair level. Do not regress to the simpler, pair-level-
 * only version; the POC needed two attempts to get this right.
 *
 * Frame *decoding* (image or representative video frame) is a platform concern handled by
 * [MediaMetadataReader] — this planner only ever sees the resulting [app.osprey.media.PixelBuffer].
 */
class VisualDupePlanner(
    private val fs: FileSystemRepository,
    private val mediaMetadataReader: MediaMetadataReader,
) {

    /**
     * Scans `root`'s immediate images/videos — subfolders, including `TODELETE`, are never
     * touched, since they simply aren't files. A file whose frame can't be decoded is silently
     * excluded (same as the POC's `None`-hash skip). Clusters of fewer than two files are
     * excluded entirely; the remaining groups are sorted largest-group-first.
     */
    fun plan(
        root: FileHandle,
        threshold: Int = VISUAL_HAMMING_THRESHOLD,
        control: JobControl = NoJobControl,
    ): VisualDupePlan {
        val files = fs.list(root).filterNot { it.isDirectory }.filter { isImageOrVideo(it.name) }
        val items = mutableListOf<Pair<FileEntry, Long>>()
        val decoded = control.forEachUntilStopped(files, FileEntry::name) { entry ->
            mediaMetadataReader.decodeFrame(entry.handle, HASH_DECODE_MAX_SIDE)?.let { items += entry to phash(it) }
        }
        if (!decoded) return VisualDupePlan(emptyList())

        val burstIds = items.map { (entry, _) -> burstId(entry.name) }
        val unionFind = UnionFind<Int>()
        for (i in items.indices) {
            for (j in (i + 1) until items.size) {
                if (burstIds[i] != null && burstIds[i] == burstIds[j]) continue
                val distance = (items[i].second xor items[j].second).countOneBits()
                if (distance <= threshold) unionFind.union(i, j)
            }
        }

        val clusters = mutableMapOf<Int, MutableList<Int>>()
        for (i in items.indices) {
            clusters.getOrPut(unionFind.find(i)) { mutableListOf() } += i
        }

        val groups = clusters.values
            .filter { it.size >= 2 }
            .map { memberIndices ->
                memberIndices.map { items[it].first }
                    .sortedByDescending { it.sizeBytes }
                    .map { VisualDupeMember(it.handle, it.name, it.sizeBytes) }
            }
            .sortedByDescending { it.size }

        return VisualDupePlan(groups)
    }

    /**
     * Logs one `GROUP` line per cluster: a `BURST SET` line listing every burst frame to keep
     * plus any non-burst member as a dup, when the cluster has >= 2 distinct burst members;
     * otherwise the heaviest member is kept and every other member is a dup. Returns
     * (group count, total dup count).
     */
    fun describe(plan: VisualDupePlan, log: (String) -> Unit): Pair<Int, Int> {
        var totalExtra = 0
        for (group in plan.groups) {
            totalExtra += describeGroup(group, log)
        }
        return plan.groups.size to totalExtra
    }

    private fun describeGroup(group: List<VisualDupeMember>, log: (String) -> Unit): Int {
        val burstMembers = group.filter { burstId(it.name) != null }
        val otherMembers = group.filter { burstId(it.name) == null }
        if (burstMembers.size < 2) {
            return describeNormalGroup(group, log)
        }
        log("  GROUP (${group.size} images) -- BURST SET, keeping all ${burstMembers.size} frames:")
        for (member in burstMembers.sortedByDescending { it.sizeBytes }) {
            log("    KEEP (burst frame) ${member.name} [${member.sizeBytes / BYTES_PER_KB}KB]")
        }
        for (member in otherMembers) {
            log("    ~dup (matches the burst) ${member.name} [${member.sizeBytes / BYTES_PER_KB}KB]")
        }
        return otherMembers.size
    }

    private fun describeNormalGroup(group: List<VisualDupeMember>, log: (String) -> Unit): Int {
        val keep = group.first()
        log("  GROUP (${group.size} images, keep=${keep.name} [${keep.sizeBytes / BYTES_PER_KB}KB])")
        for (extra in group.drop(1)) {
            log("    ~dup ${extra.name} [${extra.sizeBytes / BYTES_PER_KB}KB]")
        }
        return group.size - 1
    }

    private fun isImageOrVideo(name: String): Boolean {
        val suffix = splitStemAndSuffix(name).second.lowercase()
        return suffix in imageExtensions || suffix in videoExtensions
    }
}
