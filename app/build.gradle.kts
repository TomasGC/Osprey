plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

android {
    namespace = "app.osprey"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.osprey"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.documentfile)
    implementation(libs.exifinterface)

    testImplementation(libs.junit)
    testImplementation(testFixtures(project(":core")))
    // The JVM unit tests run without the Android framework, whose org.json is only a stub there.
    testImplementation(libs.json)

    debugImplementation(libs.compose.ui.tooling)
    // The ComponentActivity the Compose test rule of :app-instrumented launches.
    debugImplementation(libs.compose.ui.test.manifest)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("$projectDir/src/main/kotlin")
}

dependencies {
    kover(project(":core"))
    kover(project(":desktop"))
}

// Condor's coverage gate runs `koverXmlReportDebug` and reads app/build/reports/kover/reportDebug.xml. Kover 0.9 keeps
// the name `debug` for the Android variant alone, which holds none of :core and :desktop, so the merged report is the
// `merged` variant: it writes that path, and the `debug` report goes aside. `koverVerify` is not part of the tasks
// Condor runs, so the :core 100% rule (#46) hangs off the report task it does run.
tasks.matching { it.name == "koverXmlReportDebug" }.configureEach {
    dependsOn(":core:koverVerify")
    finalizedBy("koverXmlReportMerged")
}

kover {
    currentProject {
        createVariant("merged") {
            add("debug")
            addWithDependencies("jvm", optional = true)
        }
    }
    reports {
        variant("debug") {
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/reportDebugOnly.xml")
            }
        }
        variant("merged") {
            xml {
                xmlFile = layout.buildDirectory.file("reports/kover/reportDebug.xml")
            }
        }
        filters {
            excludes {
                classes(
                    "**.R",
                    "**.R$*",
                    "**.BuildConfig",
                    "**.Manifest*",
                    "**.*Test*",
                    "android.*",
                    "androidx.*",
                    // Compose UI -- checked by hand on the emulator and the desktop, outside the gate (#46)
                    "**.MainActivity*",
                )
                packages("app.osprey.android.ui", "app.osprey.desktop.ui")
                classes("MainKt*", "**.ComposableSingletons*")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
