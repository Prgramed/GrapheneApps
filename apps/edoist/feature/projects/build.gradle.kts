plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.prgramed.edoist.feature.projects"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.edoist.domain)
    implementation(projects.apps.edoist.feature.inbox)
    implementation(libs.kotlinx.datetime)
}
