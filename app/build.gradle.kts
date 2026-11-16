import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

// Java 17 bytecode, built with whichever newer JDK is installed.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // AndroidX lifecycle for desktop, from JetBrains: collectAsStateWithLifecycle and friends, as on Android.
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // The frosted tab bar, as on Android. Multiplatform, so the same library runs here.
    implementation("dev.chrisbanes.haze:haze:1.2.2")
    testImplementation("junit:junit:4.13.2")
}

// The Android app's strings, icons and fonts, read through Compose Multiplatform's resources.
compose.resources {
    publicResClass = false
    packageOfResClass = "io.github.dragjon.eigentone.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "io.github.dragjon.eigentone.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Eigentone"
            packageVersion = "0.1.0"
            vendor = "Dragjon"
            licenseFile.set(rootProject.file("LICENSE"))
        }
    }
}
