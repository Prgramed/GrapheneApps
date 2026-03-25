plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.econtacts.feature.contactedit"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.econtacts.domain)
    implementation(libs.coil.compose)
}
