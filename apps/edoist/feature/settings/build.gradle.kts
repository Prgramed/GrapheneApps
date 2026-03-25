plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.edoist.feature.settings"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.edoist.domain)
    implementation(projects.apps.edoist.data)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
}
