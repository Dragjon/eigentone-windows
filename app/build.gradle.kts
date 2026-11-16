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
            // Files that ship beside the app, such as the agent below, found at run time through the
            // compose.application.resources.dir property.
            appResourcesRootDir.set(layout.buildDirectory.dir("app-resources"))
        }
    }
}

// The agent lists the apps playing, for per-app presets. It's built with the engine (CMake) and ships with the
// app, so it runs in the user's session, where Windows keeps its audio sessions.
val copyAgent by tasks.registering(Copy::class) {
    from(rootProject.file("build/bin/eigentone-agent.exe"))
    into(layout.buildDirectory.dir("app-resources/windows"))
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(copyAgent) }
