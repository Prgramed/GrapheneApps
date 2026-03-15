plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.prgramed.eprayer.domain"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.datetime)
    implementation(libs.adhan2)
}
