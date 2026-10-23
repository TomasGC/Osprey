package app.osprey.actions.reorg

/** What Reorg does to one file. */
enum class ReorgOperation {
    /** Copy an input file into the output; the source stays. */
    COPY,

    /** Copy an input file into the output, verify it, then delete the source. */
    MOVE,

    /** Move a misfiled file to the right year folder inside the output. */
    REFILE,
}
