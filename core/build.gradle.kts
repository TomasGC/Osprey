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

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("$projectDir/src/main/kotlin")
}
