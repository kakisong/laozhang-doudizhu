buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP's built-in Kotlin defaults to an older Kotlin Gradle plugin; pinning it here makes
        // :app (built-in Kotlin) and :engine (kotlin.jvm) compile with the same Kotlin version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
