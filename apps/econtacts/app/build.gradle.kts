plugins {
    id("grapheneapps.android.application")
    id("grapheneapps.android.application.compose")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.prgramed.econtacts"

    defaultConfig {
        applicationId = "com.prgramed.econtacts"
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
    implementation(projects.apps.econtacts.domain)
    implementation(projects.apps.econtacts.data)
    implementation(projects.apps.econtacts.feature.contacts)
    implementation(projects.apps.econtacts.feature.contactedit)
    implementation(projects.apps.econtacts.feature.settings)
    implementation(projects.apps.econtacts.feature.dialer)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
