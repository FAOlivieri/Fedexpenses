import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing comes from local.properties (or FEDEXPENSES_* environment variables), which are
 * never committed. Without them, release builds fall back to the local debug key so anyone can build.
 */
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingProp(name: String): String? =
    localProps.getProperty("fedexpenses.$name") ?: System.getenv("FEDEXPENSES_" + name.uppercase())
val hasReleaseKey = signingProp("storeFile") != null

android {
    namespace = "io.github.faolivieri.fedexpenses"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.faolivieri.fedexpenses"
        minSdk = 26
        targetSdk = 35
        // Bump both for every release; Android only installs an update with a higher versionCode.
        versionCode = 9
        versionName = "1.2.3"
    }

    signingConfigs {
        if (hasReleaseKey) create("release") {
            storeFile = file(signingProp("storeFile")!!)
            storePassword = signingProp("storePassword")
            keyAlias = signingProp("keyAlias")
            keyPassword = signingProp("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

// APKs are named e.g. Fedexpenses-1.0.0-release.apk
base.archivesName = "Fedexpenses-" + android.defaultConfig.versionName

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.reorderable)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
