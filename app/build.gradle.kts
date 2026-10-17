plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

android {
    namespace = "app.osprey"
    compileSdk = 36

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    testImplementation(libs.junit)

    debugImplementation(libs.compose.ui.tooling)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    source.setFrom("$projectDir/src/main/kotlin")
}

dependencies {
    kover(project(":core"))
}

koverReport {
    androidReports("debug") {
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
                    // Compose UI -- manual, see contexts/tests.md convention from Anglerfish/Otter
                    "**.MainActivity*",
                )
            }
        }
    }
}
