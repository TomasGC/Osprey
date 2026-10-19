plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
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

    testImplementation(libs.junit)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
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
        compileClasspath += sourceSets.test.get().compileClasspath
        runtimeClasspath += sourceSets.test.get().runtimeClasspath + output
    }
    create("integrationReal") {
        kotlin.srcDir("src/integrationReal/kotlin")
        compileClasspath += sourceSets.test.get().compileClasspath
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
