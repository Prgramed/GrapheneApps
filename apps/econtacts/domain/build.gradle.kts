plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.prgramed.econtacts.domain"
}

dependencies {
    implementation(projects.core.common)
}
