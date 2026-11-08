plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example.terminal"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:engine"))
  implementation(project(":core:game"))
  implementation(project(":core:platform"))
  implementation(project(":core:render"))
  implementation(project(":core:persistence"))
  implementation(libs.androidx.room.runtime)
  implementation(project(":core:content"))
  implementation(project(":core:audio"))
  implementation(project(":core:designsystem"))
  implementation(project(":feature:combat"))
  implementation(project(":feature:exploration"))
  implementation(project(":feature:inventory"))
  implementation(project(":feature:cosmetic"))
  implementation(project(":feature:skilltree"))
  implementation(project(":feature:hacking"))
  implementation(project(":feature:cybergl"))
  implementation(project(":feature:widgets"))
  implementation(project(":feature:exploration-ui"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.kotlinx.coroutines.core)
}
