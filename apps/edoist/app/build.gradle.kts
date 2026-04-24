plugins {
    id("grapheneapps.android.application")
    id("grapheneapps.android.application.compose")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.prgramed.edoist"

    defaultConfig {
        applicationId = "com.prgramed.edoist"
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
    implementation(projects.apps.edoist.domain)
    implementation(projects.apps.edoist.data)
    implementation(projects.apps.edoist.feature.today)
    implementation(projects.apps.edoist.feature.inbox)
    implementation(projects.apps.edoist.feature.search)
    implementation(projects.apps.edoist.feature.projects)
    implementation(projects.apps.edoist.feature.taskdetail)
    implementation(projects.apps.edoist.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
