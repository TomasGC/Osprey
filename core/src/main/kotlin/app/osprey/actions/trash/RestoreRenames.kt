package app.osprey.actions.trash

import app.osprey.actions.splitStemAndSuffix

/**
 * The names to restore under when files of one restore would meet in one year folder (#85). Of the files that share
 * a name in a year, the one whose action comes first in [TrashAction] order (the tab order) keeps it; each later one
 * becomes `<stem>_<ActionFolder><suffix>`, or the next free `__dupN` form when that name is taken too.
 * [libraryNames] are the names each year folder already holds, for every year of [direct]; a name any file of the
 * restore keeps is taken as well. Returns the new names by [TrashEntry.label]; a file that keeps its name is not in it.
 */
internal fun restoreRenames(direct: List<TrashEntry>, libraryNames: Map<String, Set<String>>): Map<String, String> {
    val renames = mutableMapOf<String, String>()
    for ((year, ofYear) in direct.groupBy { it.year }) {
        val taken = (libraryNames.getValue(year) + ofYear.map { it.name }).toMutableSet()
        for (sameName in ofYear.groupBy { it.name }.values) {
            for (later in sameName.sortedBy { it.action }.drop(1)) {
                val (stem, suffix) = splitStemAndSuffix(later.name)
                val name = freeTrashName("${stem}_${later.action.folderName}$suffix", taken)
                // Reserved although no two candidates collide with today's folder names: a future action whose
                // folder name ends another's would otherwise give two files one name.
                taken += name
                renames[later.label] = name
            }
        }
    }
    return renames
}
