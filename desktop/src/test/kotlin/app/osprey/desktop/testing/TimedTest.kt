package app.osprey.desktop.testing

import java.io.File
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.streams.asSequence
import org.junit.Rule
import org.junit.Test

/** How long a `:desktop` test may run before it fails with its own name (#89). */
const val DEFAULT_TEST_LIMIT_SECONDS = 120L

/**
 * Base of every `:desktop` test class: a test that waits for something that never comes fails after
 * [limitSeconds] and names itself, instead of hanging the run. A class with a slow test on purpose
 * raises its own limit: `class MyTest : TimedTest(limitSeconds = 300)`.
 */
open class TimedTest(limitSeconds: Long = DEFAULT_TEST_LIMIT_SECONDS) {

    @get:Rule
    val timeout: SameThreadTimeout = SameThreadTimeout(limitSeconds, TimeUnit.SECONDS)
}

/**
 * The test classes next to [anchor] (same compiled-classes folder, so the same tier) that do not extend
 * [TimedTest], sorted by name. Each tier asserts this is empty.
 */
fun untimedTestClasses(anchor: Class<*>): List<String> {
    val root = Paths.get(anchor.protectionDomain.codeSource.location.toURI())
    return Files.walk(root).use { paths ->
        paths.asSequence()
            .map { root.relativize(it).toString() }
            .filter { it.endsWith(".class") }
            .map { Class.forName(it.removeSuffix(".class").replace(File.separatorChar, '.'), false, anchor.classLoader) }
            .filter { !Modifier.isAbstract(it.modifiers) && it.methods.any { m -> m.isAnnotationPresent(Test::class.java) } }
            .filterNot { TimedTest::class.java.isAssignableFrom(it) }
            .map { it.name }
            .sorted()
            .toList()
    }
}
