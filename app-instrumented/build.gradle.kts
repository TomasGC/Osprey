plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.osprey.instrumented"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Not the AVD the tests actually run on (Condor's instrumented workflow creates that one with avdmanager):
        // this declaration only makes `./gradlew pixel4api30Setup` a real task, so CI can download and cache the
        // system image first. The same declaration as in Anglerfish.
        managedDevices {
            allDevices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel4api30").apply {
                    device = "Pixel 4"
                    apiLevel = 30
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(testFixtures(project(":core")))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.documentfile)
    implementation(libs.exifinterface)
    implementation(libs.junit)
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    // Newer than the 3.5.0 the Compose BOM brings in: that one calls InputManager.getInstance, gone on API 37 (#75).
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.compose.ui.test.junit4)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
