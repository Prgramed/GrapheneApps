plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.eprayer.feature.widget"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.eprayer.domain)

    implementation(projects.apps.eprayer.data)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
}
