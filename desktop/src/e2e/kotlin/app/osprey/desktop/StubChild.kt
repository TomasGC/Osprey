package app.osprey.desktop

import java.io.BufferedOutputStream
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream

private const val BUFFER_BYTES = 8192

/**
 * A child process for [ChildProcessEndToEndTest] (#124). `ready` says READY and waits; `late` says something else
 * first, then READY; `buffered` says READY through an output that keeps what it is given until it is flushed; `almost`
 * says READY followed by more on the same line; `hang` says something else and waits; anything else says something
 * else and ends.
 */
fun main(args: Array<String>) {
    when (args.single()) {
        "ready" -> blockAfterSaying(READY)
        "buffered" -> {
            System.setOut(PrintStream(BufferedOutputStream(FileOutputStream(FileDescriptor.out), BUFFER_BYTES), false))
            blockAfterSaying(READY)
        }
        "late" -> {
            println("warming up")
            blockAfterSaying(READY)
        }
        "almost" -> blockAfterSaying("$READY now")
        "hang" -> blockAfterSaying("starting")
        else -> println("goodbye")
    }
}
