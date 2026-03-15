plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.eprayer.feature.settings"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.eprayer.domain)
    implementation(libs.kotlinx.datetime)
}
