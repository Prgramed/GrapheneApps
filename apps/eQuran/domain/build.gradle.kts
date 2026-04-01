plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "dev.equran.domain"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.datetime)
}
