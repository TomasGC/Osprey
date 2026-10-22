package app.osprey.android

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
import app.osprey.actions.yearfolders.YearFoldersPlan
import app.osprey.actions.yearfolders.YearFoldersPlanner
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

/** Unlike the other tabs this one runs on the library root, not the selected year folder (#36). */
fun yearFoldersController(
    fs: FileSystemRepository,
    mediaMetadataReader: MediaMetadataReader,
) = SimpleActionController<YearFoldersPlan>(
    scan = { root ->
        val planner = YearFoldersPlanner(fs, mediaMetadataReader)
        val plan = planner.plan(root)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count  undated=${plan.undated.size}", lines, count)
    },
    apply = { root, plan ->
        YearFoldersPlanner(fs, mediaMetadataReader).apply(plan, root)
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
 * Scanning works exactly like desktop (reads EXIF/video metadata, decides what each file's
 * timestamp should be). Applying is a confirmed, permanent no-op on Android (#13): SAF's
 * `DocumentsContract` has no writable last-modified column, and scoped storage blocks a raw-path
 * write regardless of how the handle was obtained. `fs.writeTimestamps` is still called -- same
 * shape as desktop, [AndroidFileSystemRepository] just does nothing -- but the Dates screen (#14)
 * disables its Apply button and shows that limitation as copy, rather than ever claiming a
 * timestamp actually changed.
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
