plugins {
    id("grapheneapps.android.application")
    id("grapheneapps.android.application.compose")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.prgramed.eprayer"

    defaultConfig {
        applicationId = "com.prgramed.eprayer"
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
    implementation(projects.apps.eprayer.domain)
    implementation(projects.apps.eprayer.data)
    implementation(projects.apps.eprayer.feature.prayertimes)
    implementation(projects.apps.eprayer.feature.qibla)
    implementation(projects.apps.eprayer.feature.settings)
    implementation(projects.apps.eprayer.feature.widget)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
}
