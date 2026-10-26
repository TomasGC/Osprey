package app.osprey.jobs

/**
 * Runs [action] on each of [items] in order, telling the job which one is next ([label] names it), and
 * stops before the next item once a stop was requested. This is the one loop every action's scan and
 * apply goes through, so progress and Stop behave the same everywhere. Returns true when every item was
 * handled, false when it stopped before the end.
 */
fun <T> JobControl.forEachUntilStopped(items: Collection<T>, label: (T) -> String, action: (T) -> Unit): Boolean {
    for ((index, item) in items.withIndex()) {
        if (shouldStop()) return false
        report(index, items.size, label(item))
        action(item)
    }
    return true
}
