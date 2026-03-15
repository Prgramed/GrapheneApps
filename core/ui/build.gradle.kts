plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.library.compose")
}

android {
    namespace = "com.grapheneapps.core.ui"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
