plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
    id("grapheneapps.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.grapheneapps.enotes.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.apps.enotes.domain)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.timber)
    implementation(libs.androidx.biometric)
}
