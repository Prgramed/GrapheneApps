plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
    id("grapheneapps.android.room")
}

android {
    namespace = "com.prgramed.eprayer.data"
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.apps.eprayer.domain)

    implementation(libs.adhan2)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
