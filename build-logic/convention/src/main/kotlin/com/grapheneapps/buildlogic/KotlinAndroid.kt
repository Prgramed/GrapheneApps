package com.grapheneapps.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlinAndroid(
    applicationExtension: ApplicationExtension,
) {
    applicationExtension.apply {
        compileSdk = 35
        defaultConfig {
            minSdk = 34
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlinJvm()
}

internal fun Project.configureKotlinAndroid(
    libraryExtension: LibraryExtension,
) {
    libraryExtension.apply {
        compileSdk = 35
        defaultConfig {
            minSdk = 34
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    configureKotlinJvm()
}

private fun Project.configureKotlinJvm() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}
