plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.prgramed.edoist.domain"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.datetime)
}
