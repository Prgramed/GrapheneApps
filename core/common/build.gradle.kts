plugins {
    id("grapheneapps.android.library")
}

android {
    namespace = "com.grapheneapps.core.common"
}

dependencies {
    implementation(libs.kotlinx.datetime)
}
