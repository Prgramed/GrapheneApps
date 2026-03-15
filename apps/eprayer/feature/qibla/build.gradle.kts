plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.eprayer.feature.qibla"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.eprayer.domain)
}
