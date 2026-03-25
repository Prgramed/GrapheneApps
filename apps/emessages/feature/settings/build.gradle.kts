plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.emessages.feature.settings"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.emessages.domain)
    implementation(projects.apps.emessages.data)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
}
