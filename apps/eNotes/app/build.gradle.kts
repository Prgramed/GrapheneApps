plugins {
    id("grapheneapps.android.application")
    id("grapheneapps.android.application.compose")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.grapheneapps.enotes"

    defaultConfig {
        applicationId = "com.grapheneapps.enotes"
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
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.common)
    implementation(projects.apps.enotes.domain)
    implementation(projects.apps.enotes.data)
    implementation(projects.apps.enotes.feature.notes)
    implementation(projects.apps.enotes.feature.editor)
    implementation(projects.apps.enotes.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
