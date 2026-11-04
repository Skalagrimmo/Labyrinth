import org.gradle.api.tasks.compile.JavaCompile

plugins {
  alias(libs.plugins.kotlin.jvm)
}

tasks.withType<JavaCompile>().configureEach {
  options.release.set(11)
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
}
