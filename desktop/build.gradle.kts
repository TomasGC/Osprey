plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.metadata.extractor)
    implementation(libs.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core")))
    testImplementation(compose.desktop.uiTestJUnit4)

    constraints {
        // ui-test-junit4 brings Truth, which asks for Guava 28.1-android (two known CVEs, flagged by the dependency
        // scan); this one is the latest stable and is only on the test classpath.
        testImplementation("com.google.guava:guava:33.8.0-jre")
    }
}

testing {
    suites {
        val test = getByName<JvmTestSuite>("test").apply {
            useJUnit()
        }
    }
}

// integrationMock/integrationReal reuse `test`'s own resolved classpath (not a separate
// project()-style JvmTestSuite) so they automatically inherit every `main` dependency
// (:core, Compose runtime, metadata-extractor, org.json) the same way the default `test`
// source set already does -- a JvmTestSuite's `project()` dependency only sees `main`'s
// published classes, not its `implementation`-scoped dependencies.
sourceSets {
    create("integrationMock") {
        kotlin.srcDir("src/integrationMock/kotlin")
        compileClasspath += sourceSets.test.get().compileClasspath + sourceSets.test.get().output
        runtimeClasspath += sourceSets.test.get().runtimeClasspath + output
    }
    // Every tier sees `test`'s own classes: `TimedTest`, the base of every test class (#89); integrationReal and e2e
    // also use it for the shared test photos (`app.osprey.desktop.testing`).
    // integrationReal also sees integrationMock's output: the harness that runs an action over a temp library (#79).
    create("integrationReal") {
        kotlin.srcDir("src/integrationReal/kotlin")
        val mock = named("integrationMock").get().output
        compileClasspath += sourceSets.test.get().compileClasspath + sourceSets.test.get().output + mock
        runtimeClasspath += sourceSets.test.get().runtimeClasspath + output + mock
    }
    create("e2e") {
        kotlin.srcDir("src/e2e/kotlin")
        compileClasspath += sourceSets.test.get().compileClasspath + sourceSets.test.get().output
        runtimeClasspath += sourceSets.test.get().runtimeClasspath + output
    }
}

tasks.register<Test>("integrationMock") {
    testClassesDirs = sourceSets["integrationMock"].output.classesDirs
    classpath = sourceSets["integrationMock"].runtimeClasspath
    useJUnit()
}

tasks.register<Test>("integrationReal") {
    testClassesDirs = sourceSets["integrationReal"].output.classesDirs
    classpath = sourceSets["integrationReal"].runtimeClasspath
    useJUnit()
}

// The real app, headless, on a temp library (#51). Not part of the coverage figure: it answers "does the app work
// here", not "is the logic exercised".
tasks.register<Test>("e2e") {
    testClassesDirs = sourceSets["e2e"].output.classesDirs
    classpath = sourceSets["e2e"].runtimeClasspath
    // A test that starts the app as its own process (#79) gives it the same classpath.
    systemProperty("osprey.e2e.classpath", classpath.asPath)
    useJUnit()
}

kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("e2e")
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            packageName = "Osprey"
            packageVersion = "1.0.0"
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("$projectDir/src/main/kotlin")
}
