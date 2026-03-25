plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.emessages.feature.conversations"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.emessages.domain)
    implementation(projects.apps.emessages.data)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
