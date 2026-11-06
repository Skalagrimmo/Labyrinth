plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.example.data"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

// Export Room schema JSON to core/platform/schemas so the v6 migration baseline
// stays pinned by SchemaV6CompatibilityTest while the Room layer lives here.
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:engine"))
  implementation(project(":core:persistence"))
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.kotlinx.coroutines.core)
  "ksp"(libs.androidx.room.compiler)
  testImplementation(libs.junit)
  // JVM tests need a real JSON implementation; production keeps Android org.json for API compatibility.
  testImplementation("org.json:json:20240303")
}
