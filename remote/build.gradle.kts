plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

apply(from = rootProject.file("gradle/versioning.gradle.kts"))

android {
    namespace = "com.amosley.hearth.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.amosley.hearth.remote"
        minSdk = 26
        targetSdk = 34
        versionCode = extra["hearthVersionCode"] as Int
        versionName = extra["hearthVersionName"] as String
        buildConfigField("String", "GITHUB_REPO", "\"amosley0221/Hearth\"")
    }

    // One fixed release key (from CI secrets) signs every release, so new
    // versions always install over the old one without uninstalling.
    val ksFile = System.getenv("HEARTH_KEYSTORE_FILE")?.let { file(it) }
    signingConfigs {
        if (ksFile != null && ksFile.exists()) {
            create("release") {
                storeFile = ksFile
                storePassword = System.getenv("HEARTH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HEARTH_KEY_ALIAS")
                keyPassword = System.getenv("HEARTH_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf(
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/INDEX.LIST",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.coil.compose)
    implementation(libs.bouncycastle.pkix)
}
