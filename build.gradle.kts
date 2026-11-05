buildscript {
    dependencies {
        constraints {
            // The Android Gradle Plugin 9.5.1 brings Guava 33.4.0, affected by GHSA-xxph-c9ww-hj94 (fixed in 33.7.2).
            // Remove once the AGP ships a Guava of 33.7.2 or later; look again on 2026-12-05.
            classpath("com.google.guava:guava:33.8.0-jre")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt) apply false
}
