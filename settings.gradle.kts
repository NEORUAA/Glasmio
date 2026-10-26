pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Glasmio"
include(":glasmio-core", ":glasmio-glass", ":glasmio-ui", ":glasmio-scaffold", ":glasmio-preference", ":glasmio-icons", ":sample")
