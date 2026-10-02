import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // The Kotlin Gradle plugin comes from the root buildscript classpath, so no version here.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    // The Java API is shared; Android supplies its own native runtime through the app module.
    compileOnly(libs.onnxruntime.jvm)
    testImplementation(libs.onnxruntime.jvm)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("slow")
    }
    maxHeapSize = "2g"
    testLogging {
        events("failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Long-running checks: AI strength benchmarks, bidding-model calibration, large self-play runs.
tasks.register<Test>("slowTest") {
    description = "Runs tests tagged 'slow' (benchmarks and calibration)."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("slow")
    }
    maxHeapSize = "4g"
    // Forward -Dddz.* tuning knobs (deal counts, sample budgets) to the test JVM.
    systemProperties(System.getProperties().filterKeys { it.toString().startsWith("ddz.") }.mapKeys { it.key.toString() })
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
}
