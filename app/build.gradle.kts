import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing credentials live outside the repository, in keystore.properties next
// to this file's project root. The keystore itself and that file are both
// gitignored: a signing key in version control is a signing key anyone with
// read access can publish under.
//
// When the file is absent the release build simply stays unsigned rather than
// failing, so a clean checkout still builds. `signingReady` below is what
// decides, and the release variant only gets a signingConfig when it is true.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val signingReady = keystoreProperties.getProperty("storeFile")?.let {
    rootProject.file(it).exists()
} == true

// True when this invocation asked for an App Bundle, however it was spelled —
// "bundleRelease", ":app:bundleRelease", or the task Android Studio runs for
// Generate Signed App Bundle.
val buildingBundle = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').startsWith("bundle", ignoreCase = true)
}

android {
    namespace = "com.denguewatch.bd"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.denguewatch.bd"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Absent a keystore this stays null and the output is unsigned —
            // the same artefact as before, rather than a build failure.
            signingConfig = if (signingReady) signingConfigs.getByName("release") else null

            // MapLibre ships a ~12MB native library per ABI and there are four
            // of them, which took the release APK from 14MB to 62MB. R8 shrinks
            // the ~30MB of dex that rides along with Compose; the ABI splits
            // below deal with the native half.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // One APK per architecture rather than one carrying all four. A phone only
    // ever needs its own, and shipping the other three triples the download for
    // readers on the kind of connection this app is meant to work on.
    //
    // These splits and an App Bundle cannot both be produced in one build: with
    // resource shrinking on, AGP emits one shrunk-resources file per ABI and the
    // bundle task then fails with "Multiple shrunk-resources files found". So a
    // bundle build turns them off on its own.
    //
    // That last part is the whole point of `buildingBundle`. It used to require
    // `-PabiSplits=false` by hand, which meant Android Studio's Build > Generate
    // Signed App Bundle — the normal way to produce a Play upload — failed, with
    // an error naming intermediate files rather than the cause. An App Bundle
    // already splits by ABI on Play's side, so nothing is lost by disabling them
    // whenever a bundle is what was asked for.
    //
    //     ./gradlew assembleRelease   -> per-ABI APKs
    //     ./gradlew bundleRelease     -> app-release.aab for Play
    splits {
        abi {
            isEnable = !buildingBundle && providers.gradleProperty("abiSplits")
                .getOrElse("true").toBoolean()
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // Every language in every install. The app switches between English and
    // Bangla itself, independent of the phone's language, and the date and time
    // pickers follow it by loading Android's Bangla resources. Play splits an
    // App Bundle by the phone's languages, so on an English phone those
    // resources would never be installed and the pickers would fall back to
    // English inside a Bangla screen.
    bundle {
        language {
            enableSplit = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.location)
    implementation(libs.androidx.fragment)
    implementation(libs.maplibre.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.mlkit.text.recognition)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
