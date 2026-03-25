plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.edoist.feature.inbox"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.edoist.domain)
    implementation(libs.kotlinx.datetime)
}
