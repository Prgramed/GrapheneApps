plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
    id("grapheneapps.android.room")
}

android {
    namespace = "com.prgramed.econtacts.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.apps.econtacts.domain)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.timber)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
