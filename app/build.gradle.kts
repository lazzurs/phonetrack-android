plugins {
    alias(libs.plugins.android.application)
}

// Fork release signing: CI decodes the RELEASE_KEYSTORE_BASE64 secret to a file and passes its
// path here via env vars, so releases are signed consistently across builds. Without those env
// vars (local dev builds), `release` falls back to the debug signing config so it still installs.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseKeystorePassword: String? = System.getenv("RELEASE_KEYSTORE_PASSWORD")
val hasReleaseSigning = !releaseKeystorePath.isNullOrEmpty() && !releaseKeystorePassword.isNullOrEmpty()

android {
    namespace = "net.eneiluj.nextcloud.phonetrack"
    // compileSdk tracks the newest stable platform; targetSdk is what Google Play
    // currently requires (API 36 since 2026-08-31). Raising targetSdk opts the app
    // into that release's behavior changes, so bump it deliberately and test.
    compileSdk = 37

    defaultConfig {
        applicationId = "net.eneiluj.nextcloud.phonetrack"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "0.1.1"
        vectorDrawables.useSupportLibrary = true
        resValue("string", "applicationId", "net.eneiluj.nextcloud.phonetrack")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "phonetrack-fork"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: releaseKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate code, then drop unused resources.
            // The mapping file (for readable stack traces) is attached to each GitHub release.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // AGP 9 defaults this to false; defaultConfig/flavors below use resValue
        resValues = true
        // solves cert4android crash
        dataBinding = true
    }

    androidResources {
        // Generates the locale_config used by Android 13+ per-app language settings
        // from the values-* folders (default locale set in res/resources.properties).
        generateLocaleConfig = true
    }

    flavorDimensions += "default"
    productFlavors {
        create("normal") {
            dimension = "default"
            resValue("string", "app_name", "PhoneTrack")
        }
        create("dev") {
            dimension = "default"
            applicationId = "net.eneiluj.nextcloud.phonetrack.dev"
            resValue("string", "applicationId", "net.eneiluj.nextcloud.phonetrack.dev")
            resValue("string", "app_name", "PhoneTrack Dev")
        }
        create("play") {
            dimension = "default"
            applicationId = "net.eneiluj.nextcloud.phonetrack.play"
            resValue("string", "applicationId", "net.eneiluj.nextcloud.phonetrack.play")
            resValue("string", "app_name", "PhoneTrack")
        }
    }

    testOptions {
        // Robolectric needs the merged manifest and resources
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = false
        disable += "MissingTranslation"
    }
}

// Robolectric needs Java 21 to emulate SDK 35+. Only the unit tests run on it: the build
// (and the bytecode target, see compileOptions) stays on JDK 17.
tasks.withType<Test>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
    // Robolectric's SDK 36 sandbox sets up shared memory through FileDescriptor internals
    jvmArgs(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    )
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime)
    // needed for cert4android (conflict resolution)
    implementation(libs.androidx.cardview)
    implementation(libs.material)

    implementation(libs.cert4android)
    implementation(libs.conscrypt.android)
    implementation(libs.nextcloud.sso)
    implementation(libs.gson)

    implementation(libs.clans.fab)
    implementation(libs.osmdroid.android)
    implementation(libs.osmdroid.mapsforge)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.junit)
}
