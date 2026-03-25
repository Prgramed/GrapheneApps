plugins {
    id("grapheneapps.android.application")
    id("grapheneapps.android.application.compose")
    id("grapheneapps.android.hilt")
    id("grapheneapps.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.ecalendar"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "dev.ecalendar"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // AndroidX & Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // iCalendar
    implementation(libs.ical4j)

    // Networking
    implementation(libs.okhttp)

    // DateTime
    implementation(libs.kotlinx.datetime)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Security (EncryptedSharedPreferences)
    implementation(libs.androidx.security.crypto)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)
}
