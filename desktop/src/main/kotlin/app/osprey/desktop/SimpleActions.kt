package app.osprey.desktop

import app.osprey.actions.dedupe.DedupePlan
import app.osprey.actions.dedupe.DedupePlanner
import app.osprey.actions.dates.DatesPlan
import app.osprey.actions.dates.DatesPlanner
import app.osprey.actions.flatten.FlattenPlan
import app.osprey.actions.flatten.FlattenPlanner
import app.osprey.actions.rename.RenamePlan
import app.osprey.actions.rename.RenamePlanner
import app.osprey.actions.standardize.StandardizePlan
import app.osprey.actions.standardize.StandardizePlanner
import app.osprey.actions.trash.TrashLocation
import app.osprey.actions.trash.TrashMigrationPlan
import app.osprey.actions.trash.TrashMigrationPlanner
import app.osprey.filesystem.FileSystemRepository
import app.osprey.filesystem.FileTimestamps
import app.osprey.media.MediaMetadataReader
import java.time.ZoneId

fun flattenController(fs: FileSystemRepository) = SimpleActionController<FlattenPlan>(
    scan = { yearDir ->
        val planner = FlattenPlanner(fs)
        val plan = planner.plan(yearDir)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count", lines, count)
    },
    apply = { yearDir, plan ->
        FlattenPlanner(fs).apply(plan, yearDir) {}
        plan.moves.size
    },
)

fun renameController(fs: FileSystemRepository) = SimpleActionController<RenamePlan>(
    scan = { yearDir ->
        val planner = RenamePlanner(fs)
        val plan = planner.plan(yearDir)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count  skipped=${plan.skips.size}", lines, count)
    },
    apply = { _, plan ->
        RenamePlanner(fs).apply(plan)
        plan.renames.size
    },
)

fun dedupeController(
    fs: FileSystemRepository,
    trashLocation: () -> TrashLocation,
) = SimpleActionController<DedupePlan>(
    scan = { yearDir ->
        val planner = DedupePlanner(fs)
        val plan = planner.plan(yearDir, trashLocation())
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count", lines, count)
    },
    apply = { yearDir, plan ->
        DedupePlanner(fs).apply(plan, trashLocation())
        plan.moves.size
    },
)

/**
 * Applying a resolved date is deliberately this tab's own job, not `:core`'s (#6): `DatesPlanner`
 * only decides *what* a file's timestamp should be. Both created and modified are set, matching
 * the POC's `apply_dates` (portable `os.utime` for both, plus Windows Created-time).
 */
fun datesController(
    fs: FileSystemRepository,
    mediaMetadataReader: MediaMetadataReader,
) = SimpleActionController<DatesPlan>(
    scan = { yearDir ->
        val planner = DatesPlanner(fs, mediaMetadataReader)
        val plan = planner.plan(yearDir)
        val lines = mutableListOf<String>()
        val (fixedMeta, fixedFilename) = planner.describe(plan, lines::add)
        val summary = "count=${plan.fixes.size}  fixed_meta=$fixedMeta  " +
            "fixed_filename=$fixedFilename  unfixable=${plan.unfixable.size}"
        ScanResult(plan, summary, lines, plan.fixes.size)
    },
    apply = { _, plan ->
        for (fix in plan.fixes) {
            val instant = fix.dateTime.atZone(ZoneId.systemDefault()).toInstant()
            fs.writeTimestamps(fix.entry, FileTimestamps(created = instant, modified = instant))
        }
        plan.fixes.size
    },
)

/** Runs on the library root: moves the old `<year>/TODELETE*` folders into `Trash` (#38). */
fun trashMigrationController(fs: FileSystemRepository) = SimpleActionController<TrashMigrationPlan>(
    scan = { root ->
        val planner = TrashMigrationPlanner(fs)
        val plan = planner.plan(root)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count  old_folders=${plan.folders.size}", lines, count + plan.folders.size)
    },
    apply = { root, plan ->
        TrashMigrationPlanner(fs).apply(plan, root) {}
        plan.moves.size
    },
)

fun standardizeController(
    fs: FileSystemRepository,
    mediaMetadataReader: MediaMetadataReader,
) = SimpleActionController<StandardizePlan>(
    scan = { yearDir ->
        val planner = StandardizePlanner(fs, mediaMetadataReader)
        val plan = planner.plan(yearDir)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count  unfixable=${plan.unfixable.size}", lines, count)
    },
    apply = { _, plan ->
        StandardizePlanner(fs, mediaMetadataReader).apply(plan)
        plan.renames.size
    },
)
