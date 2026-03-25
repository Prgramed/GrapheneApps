plugins {
    id("grapheneapps.android.library")
    id("grapheneapps.android.hilt")
}

android {
    namespace = "com.grapheneapps.enotes.domain"
}

dependencies {
    implementation(projects.core.common)
}
