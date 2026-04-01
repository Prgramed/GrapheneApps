plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
    id("grapheneapps.android.room")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.equran.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.apps.equran.domain)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore.preferences)

    // Networking (for tafsir + word-by-word APIs)
    implementation(libs.okhttp)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.serialization)

    implementation(libs.timber)
}
