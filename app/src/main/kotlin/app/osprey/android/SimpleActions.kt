package app.osprey.android

import app.osprey.actions.dates.DatesPlan
import app.osprey.actions.dates.DatesPlanner
import app.osprey.actions.dedupe.DedupePlan
import app.osprey.actions.dedupe.DedupePlanner
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
import app.osprey.jobs.JobQueue
import app.osprey.media.MediaMetadataReader

fun flattenController(fs: FileSystemRepository, jobs: JobQueue) = SimpleActionController<FlattenPlan>(
    name = "Flatten",
    jobs = jobs,
    scan = { yearDir, control ->
        val planner = FlattenPlanner(fs)
        val plan = planner.plan(yearDir, control)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count", lines, count)
    },
    apply = { yearDir, plan, control -> FlattenPlanner(fs).apply(plan, yearDir, control) {} },
)

fun renameController(fs: FileSystemRepository, jobs: JobQueue) = SimpleActionController<RenamePlan>(
    name = "Rename",
    jobs = jobs,
    scan = { yearDir, control ->
        val planner = RenamePlanner(fs)
        val plan = planner.plan(yearDir, control)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count  skipped=${plan.skips.size}", lines, count)
    },
    apply = { _, plan, control -> RenamePlanner(fs).apply(plan, control) },
)

fun dedupeController(
    fs: FileSystemRepository,
    jobs: JobQueue,
    trashLocation: () -> TrashLocation,
) = SimpleActionController<DedupePlan>(
    name = "Dedupe",
    jobs = jobs,
    scan = { yearDir, control ->
        val planner = DedupePlanner(fs)
        val plan = planner.plan(yearDir, trashLocation(), control)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count", lines, count)
    },
    apply = { _, plan, control -> DedupePlanner(fs).apply(plan, trashLocation(), control = control) },
)

/**
 * Deciding what a file's timestamp should be is [DatesPlanner]'s job and so is applying it: both created and
 * modified are set, matching the POC's `apply_dates` (portable `os.utime` for both, plus Windows Created-time).
 */
fun datesController(
    fs: FileSystemRepository,
    mediaMetadataReader: MediaMetadataReader,
    jobs: JobQueue,
) = SimpleActionController<DatesPlan>(
    name = "Dates",
    jobs = jobs,
    scan = { yearDir, control ->
        val planner = DatesPlanner(fs, mediaMetadataReader)
        val plan = planner.plan(yearDir, control)
        val lines = mutableListOf<String>()
        val (fixedMeta, fixedFilename) = planner.describe(plan, lines::add)
        val summary = "count=${plan.fixes.size}  fixed_meta=$fixedMeta  " +
            "fixed_filename=$fixedFilename  unfixable=${plan.unfixable.size}"
        ScanResult(plan, summary, lines, plan.fixes.size)
    },
    apply = { _, plan, control -> DatesPlanner(fs, mediaMetadataReader).apply(plan, control = control) },
)

/** Runs on the library root: moves the old `<year>/TODELETE*` folders into `Trash` (#38). */
fun trashMigrationController(
    fs: FileSystemRepository,
    jobs: JobQueue,
    onApplied: () -> Unit = {},
) = SimpleActionController<TrashMigrationPlan>(
    name = "Trash migration",
    jobs = jobs,
    scan = { root, control ->
        val planner = TrashMigrationPlanner(fs)
        val plan = planner.plan(root, control)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count  old_folders=${plan.folders.size}", lines, count + plan.folders.size)
    },
    apply = { root, plan, control -> TrashMigrationPlanner(fs).apply(plan, root, control) {} },
    onApplied = onApplied,
)

fun standardizeController(
    fs: FileSystemRepository,
    mediaMetadataReader: MediaMetadataReader,
    jobs: JobQueue,
) = SimpleActionController<StandardizePlan>(
    name = "Standardize",
    jobs = jobs,
    scan = { yearDir, control ->
        val planner = StandardizePlanner(fs, mediaMetadataReader)
        val plan = planner.plan(yearDir, control)
        val lines = mutableListOf<String>()
        val count = planner.describe(plan, lines::add)
        ScanResult(plan, "count=$count  unfixable=${plan.unfixable.size}", lines, count)
    },
    apply = { _, plan, control -> StandardizePlanner(fs, mediaMetadataReader).apply(plan, control) },
)
