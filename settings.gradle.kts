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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Netcrawler"

include(":app")
include(":core:model")
include(":core:content")
include(":core:engine")
include(":core:persistence")
include(":core:render")
include(":core:game")
include(":core:audio")
include(":core:platform")
include(":core:designsystem")
include(":feature:skilltree")
include(":feature:combat")
include(":feature:exploration")
include(":feature:cosmetic")
include(":feature:inventory")
include(":feature:hacking")
include(":feature:terminal")
include(":feature:cybergl")
include(":feature:widgets")
