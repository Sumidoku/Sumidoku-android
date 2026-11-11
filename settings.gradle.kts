pluginManagement {
    repositories {
        google()
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

rootProject.name = "sumidoku-android"

include(":app")

// Feature layer (UI + ViewModel, Compose) — see docs/android-client.md §4
include(":feature:servers")
include(":feature:library")
include(":feature:browse")
include(":feature:manga")
include(":feature:reader")
include(":feature:history")
include(":feature:updates")
include(":feature:downloads")
include(":feature:migrate")
include(":feature:trackers")
include(":feature:settings")

// Core layer
include(":core:source")
include(":core:data")
include(":core:download")
include(":core:stream")
include(":core:network")
include(":core:api")
include(":core:model")
include(":core:common")
