package app.osprey.actions.reorg

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The two ways the review lists the same pairs. */
enum class DuplicateViewMode { MINIATURES, DETAILS }

/** One line of the review: a pair and which of its two files are ticked (kept). */
data class DuplicateRow(val duplicate: ReorgDuplicate, val keepInput: Boolean, val keepLibrary: Boolean)

/**
 * What the review dialog shows: the [rows], the [mode] of the list, the file open in the preview, if any, and the two
 * column headers.
 */
data class DuplicateReviewView(
    val rows: List<DuplicateRow>,
    val mode: DuplicateViewMode,
    val previewing: DuplicateSide?,
    val inputHeader: String,
    val libraryHeader: String,
) {
    /** The input header's box: ticked while every input file is ticked. */
    val inputColumnKept: Boolean get() = rows.all { it.keepInput }

    /** The library header's box: ticked while every library file is ticked. */
    val libraryColumnKept: Boolean get() = rows.all { it.keepLibrary }

    /** How many files Continue sends to the Trash: the unticked boxes. */
    val trashCount: Int
        get() = rows.sumOf { (if (it.keepInput) 0 else 1) + (if (it.keepLibrary) 0 else 1) }

    val footer: String
        get() = when (trashCount) {
            0 -> "No file will go to the Trash"
            1 -> "1 file will go to the Trash"
            else -> "$trashCount files will go to the Trash"
        }
}

/**
 * The logic of the "Which files do you want to keep?" dialog (#91), shared by both platforms: they only draw [view].
 * A ticked file is kept and an unticked one goes to the Trash on [confirm]. The list mode chosen last is the one the
 * next review opens in.
 */
class DuplicateReview {
    private var mode = DuplicateViewMode.MINIATURES
    private val state = MutableStateFlow<DuplicateReviewView?>(null)

    /** The open review, or null while there is none. */
    val view: StateFlow<DuplicateReviewView?> = state

    /** Opens the review on [duplicates], everything ticked; with no pair there is nothing to ask. */
    fun open(duplicates: List<ReorgDuplicate>) {
        if (duplicates.isEmpty()) return
        val sources = duplicates.map { it.source }.distinct()
        val years = duplicates.map { it.library.year }.distinct()
        state.value = DuplicateReviewView(
            rows = duplicates.map { DuplicateRow(it, keepInput = true, keepLibrary = true) },
            mode = mode,
            previewing = null,
            inputHeader = if (sources.size == 1) "Files from ${sources[0]}" else "Files from the input folders",
            libraryHeader = if (years.size == 1 && years[0].isNotEmpty()) {
                "Files already in ${years[0]}"
            } else {
                "Files already in the library"
            },
        )
    }

    fun toggleInput(index: Int) = changeRows { rows ->
        rows.mapIndexed { n, row -> if (n == index) row.copy(keepInput = !row.keepInput) else row }
    }

    fun toggleLibrary(index: Int) = changeRows { rows ->
        rows.mapIndexed { n, row -> if (n == index) row.copy(keepLibrary = !row.keepLibrary) else row }
    }

    /** The input header's box: unticks the whole column when it is full, otherwise ticks it. */
    fun toggleInputColumn() {
        val tick = view.value?.inputColumnKept == false
        changeRows { rows -> rows.map { it.copy(keepInput = tick) } }
    }

    /** The library header's box: unticks the whole column when it is full, otherwise ticks it. */
    fun toggleLibraryColumn() {
        val tick = view.value?.libraryColumnKept == false
        changeRows { rows -> rows.map { it.copy(keepLibrary = tick) } }
    }

    /** Switches the list between miniatures and details; no choice is lost. */
    fun setMode(newMode: DuplicateViewMode) {
        mode = newMode
        state.value = state.value?.copy(mode = newMode)
    }

    /** Opens the preview on [side], or closes it when [side] is null. */
    fun preview(side: DuplicateSide?) {
        state.value = state.value?.copy(previewing = side)
    }

    /** Closes the review and returns what the user chose, or null when no review is open. */
    fun confirm(): List<DuplicateChoice>? {
        val current = state.value ?: return null
        state.value = null
        return current.rows.map { DuplicateChoice(it.duplicate, it.keepInput, it.keepLibrary) }
    }

    /** Closes the review; nothing is moved. */
    fun cancel() {
        state.value = null
    }

    private fun changeRows(change: (List<DuplicateRow>) -> List<DuplicateRow>) {
        state.value = state.value?.let { it.copy(rows = change(it.rows)) }
    }
}
