plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.grapheneapps.enotes.feature.settings"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.enotes.domain)
    implementation(projects.apps.enotes.data)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.timber)
}
