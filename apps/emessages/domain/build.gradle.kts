plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.prgramed.emessages.domain"
}

dependencies {
    implementation(projects.core.common)
}
