package app.osprey.desktop

/**
 * A child process for [ChildProcessEndToEndTest] (#124): `ready` says READY and waits, `hang` says something else and
 * waits, anything else says something else and ends.
 */
fun main(args: Array<String>) {
    when (args.single()) {
        "ready" -> sayAndWait("READY")
        "hang" -> sayAndWait("starting")
        else -> println("goodbye")
    }
}

private fun sayAndWait(line: String) {
    println(line)
    System.out.flush()
    Thread.sleep(Long.MAX_VALUE)
}
