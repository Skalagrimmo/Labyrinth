plugins {
  alias(libs.plugins.kotlin.jvm)
}

dependencies {
  implementation(project(":core:model"))
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(11)
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}
