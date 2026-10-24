import kotlinx.kover.gradle.plugin.dsl.MetricType

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
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

// :core is held at 100% line and branch coverage (#46); no exclusions. A new decision in :core needs the
// test that asserts it, or `koverVerify` fails. Compose screens and the platform modules are outside it.
koverReport {
    verify {
        rule("every line of :core is run by a test") {
            bound {
                minValue = 100
                metric = MetricType.LINE
            }
        }
        rule("every branch of :core is taken both ways by a test") {
            bound {
                minValue = 100
                metric = MetricType.BRANCH
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
