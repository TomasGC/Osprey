package app.osprey.desktop

/**
 * A child process for [ChildProcessEndToEndTest] (#124). `ready` says READY and waits; `late` says something else
 * first, then READY; `almost` says READY followed by more on the same line; `hang` says something else and waits;
 * anything else says something else and ends.
 */
fun main(args: Array<String>) {
    when (args.single()) {
        "ready" -> blockAfterSaying(READY)
        "late" -> {
            println("warming up")
            blockAfterSaying(READY)
        }
        "almost" -> blockAfterSaying("$READY now")
        "hang" -> blockAfterSaying("starting")
        else -> println("goodbye")
    }
}
