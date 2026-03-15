package com.grapheneapps.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun Project.configureAndroidCompose(
    applicationExtension: ApplicationExtension,
) {
    applicationExtension.apply {
        buildFeatures {
            compose = true
        }
    }
    addComposeDependencies()
}

internal fun Project.configureAndroidCompose(
    libraryExtension: LibraryExtension,
) {
    libraryExtension.apply {
        buildFeatures {
            compose = true
        }
    }
    addComposeDependencies()
}

private fun Project.addComposeDependencies() {
    dependencies {
        val bom = platform(libs.findLibrary("androidx-compose-bom").get())
        add("implementation", bom)
        add("androidTestImplementation", bom)
        add("implementation", libs.findLibrary("androidx-compose-ui").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
        add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
        add("implementation", libs.findLibrary("androidx-compose-material3").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
        add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
    }
}

internal val Project.libs
    get(): org.gradle.api.artifacts.VersionCatalog =
        extensions.getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
            .named("libs")
