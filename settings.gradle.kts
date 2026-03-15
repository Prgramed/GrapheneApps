pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GrapheneApps"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// Core shared modules
include(":core:designsystem")
include(":core:ui")
include(":core:common")

// ePrayer app
include(":apps:eprayer:app")
include(":apps:eprayer:domain")
include(":apps:eprayer:data")
include(":apps:eprayer:feature:prayertimes")
include(":apps:eprayer:feature:qibla")
include(":apps:eprayer:feature:settings")
include(":apps:eprayer:feature:widget")
