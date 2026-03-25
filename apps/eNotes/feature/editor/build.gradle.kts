plugins {
    id("grapheneapps.android.feature")
}

android {
    namespace = "com.grapheneapps.enotes.feature.editor"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.enotes.domain)
    implementation(projects.apps.enotes.data)
}
