plugins {
  alias(libs.plugins.kotlin.jvm)
}

dependencies {
  implementation(project(":core:model"))
  implementation(project(":core:content"))
  implementation(project(":core:engine"))
  implementation(project(":core:render"))
  implementation(project(":core:persistence"))
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.junit)
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(11)
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}
