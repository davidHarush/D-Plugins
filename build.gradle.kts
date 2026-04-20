import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "com.d.h.plugins"
version = "1.0.5"

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        androidStudio("2024.2.1.9")
        bundledPlugin("org.jetbrains.android")
        pluginVerifier()
        zipSigner()
    }

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false
    projectName = project.name

    pluginConfiguration {
        id = "com.d.h.plugins.dplugins"
        name = "D-plugins"
        version = project.version.toString()
        description = """
            Records per-run Android app Logcat sessions into AppLogs inside the current project.
            Includes a right-side tool window for enabling recording, deleting captured logs, and opening the AppLogs folder.
        """.trimIndent()

        ideaVersion {
            sinceBuild = "242"
            untilBuild = "253.*"
        }

        vendor {
            name = "David Harush"
        }
    }

    pluginVerification {
        ides {
            select {
                types = listOf(IntelliJPlatformType.AndroidStudio)
                channels = listOf(ProductRelease.Channel.RELEASE, ProductRelease.Channel.PATCH)
                sinceBuild = "242"
                untilBuild = "253.*"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
