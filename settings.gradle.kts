pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
    }

    versionCatalogs {
        create("conventions") {
            from("de.charlex.conventions.kmp:catalog:2.2.0")
        }
    }
}

rootProject.name = "BillingHelper"

include(":billing")
