try {
    val processEnv = Class.forName("java.lang.ProcessEnvironment")
    val theCaseInsensitiveEnvironment = processEnv.getDeclaredField("theCaseInsensitiveEnvironment")
    theCaseInsensitiveEnvironment.isAccessible = true
    (theCaseInsensitiveEnvironment.get(null) as? MutableMap<String, String>)?.remove("ANDROID_PREFS_ROOT")
    val theEnvironment = processEnv.getDeclaredField("theEnvironment")
    theEnvironment.isAccessible = true
    (theEnvironment.get(null) as? MutableMap<String, String>)?.remove("ANDROID_PREFS_ROOT")
} catch (_: Throwable) {
}

pluginManagement {
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
/*
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
*/
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Sahara"
include(":app")
 