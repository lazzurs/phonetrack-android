pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // downloads the JDK a toolchain asks for when it isn't installed (see app/build.gradle.kts)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // JitPack builds artifacts from Git on demand, so it only serves the two libraries that
        // are published nowhere else; everything else must come from Google or Maven Central
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.bitfireat")
                includeGroup("com.github.nextcloud")
            }
        }
    }
}

rootProject.name = "phonetrack-android"
include(":app")
