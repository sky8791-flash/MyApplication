pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal() // 这一行很重要
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "MyApplication"
include(":app")