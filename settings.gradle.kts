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

// eMessages app
include(":apps:emessages:app")
include(":apps:emessages:domain")
include(":apps:emessages:data")
include(":apps:emessages:feature:conversations")
include(":apps:emessages:feature:chat")
include(":apps:emessages:feature:settings")

// eContacts app
include(":apps:econtacts:app")
include(":apps:econtacts:domain")
include(":apps:econtacts:data")
include(":apps:econtacts:feature:contacts")
include(":apps:econtacts:feature:contactedit")
include(":apps:econtacts:feature:settings")
include(":apps:econtacts:feature:dialer")

// eMusic app
include(":apps:emusic:app")

// eNotes app
include(":apps:enotes:app")
include(":apps:enotes:domain")
include(":apps:enotes:data")
include(":apps:enotes:feature:notes")
include(":apps:enotes:feature:editor")
include(":apps:enotes:feature:settings")

// eDoist app
include(":apps:edoist:app")
include(":apps:edoist:domain")
include(":apps:edoist:data")
include(":apps:edoist:feature:today")
include(":apps:edoist:feature:inbox")
include(":apps:edoist:feature:search")
include(":apps:edoist:feature:projects")
include(":apps:edoist:feature:taskdetail")
include(":apps:edoist:feature:settings")

// eWeather app
include(":apps:eweather:app")

// eCalendar app
include(":apps:ecalendar:app")

// eGallery app
include(":apps:egallery:app")
