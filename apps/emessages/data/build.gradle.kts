plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.prgramed.emessages.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.apps.emessages.domain)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
}
