plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.library.compose")
}

android {
    namespace = "com.grapheneapps.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
