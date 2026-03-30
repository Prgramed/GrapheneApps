plugins {
    id("grapheneapps.android.application")
    id("grapheneapps.android.application.compose")
    id("grapheneapps.android.hilt")
    id("grapheneapps.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.egallery"

    defaultConfig {
        applicationId = "dev.egallery"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // Core monorepo modules
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.common)

    // AndroidX
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)

    // Database & Preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)

    // Images
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)

    // Media3 (video playback)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // Map
    implementation(libs.osmdroid)

    // Background work
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Material icons
    implementation(libs.androidx.compose.material.icons.extended)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit)
}
