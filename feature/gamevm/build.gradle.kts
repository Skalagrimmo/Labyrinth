plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.example.gamevm"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:engine"))
  implementation(project(":core:game"))
  implementation(project(":core:content"))
  implementation(project(":core:persistence"))
  implementation(project(":core:platform"))
  implementation(project(":core:render"))
  implementation(project(":core:audio"))
  implementation(project(":feature:combat"))
  implementation(project(":feature:exploration"))
  implementation(project(":feature:inventory"))
  implementation(project(":feature:cosmetic"))
  implementation(project(":feature:skilltree"))
  implementation(libs.androidx.room.runtime)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.kotlinx.coroutines.core)
}
