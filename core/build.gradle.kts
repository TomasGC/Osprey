import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    // The in-memory file system and metadata reader are shared with the :app tests (#50).
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testFixturesImplementation(libs.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

testing {
    suites {
        val test = getByName<JvmTestSuite>("test").apply {
            useJUnit()
        }
        register<JvmTestSuite>("integrationMock") {
            useJUnit()
            dependencies {
                implementation(project())
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        register<JvmTestSuite>("integrationReal") {
            useJUnit()
            dependencies {
                implementation(project())
                implementation(libs.junit)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

// The test doubles shared with the other modules are not :core's code; the coverage rule is about what ships.
kover {
    currentProject {
        sources {
            excludedSourceSets.add("testFixtures")
        }
    }
}

// :core is held at 100% line and branch coverage (#46); no exclusions. A new decision in :core needs the
// test that asserts it, or `koverVerify` fails. Compose screens and the platform modules are outside it.
kover {
    reports {
        verify {
            rule("every line of :core is run by a test") {
                bound {
                    minValue = 100
                    coverageUnits = CoverageUnit.LINE
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                }
            }
            rule("every branch of :core is taken both ways by a test") {
                bound {
                    minValue = 100
                    coverageUnits = CoverageUnit.BRANCH
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                }
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("$projectDir/src/main/kotlin")
}

// The report Condor's coverage gate reads merges every module (`merged`, see :app): :core and :desktop add their JVM
// coverage to it under the same variant name.
kover {
    currentProject {
        createVariant("merged") {
            add("jvm")
        }
    }
}
