plugins { id("grapheneapps.android.feature") }
android { namespace = "com.prgramed.econtacts.feature.dialer" }
dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.apps.econtacts.domain)
    implementation(projects.apps.econtacts.data)
}
