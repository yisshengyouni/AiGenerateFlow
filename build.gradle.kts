plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.9.24"
  id("org.jetbrains.intellij") version "1.17.3"
  id("org.jetbrains.changelog") version "1.3.1"
}

group = "com.huq.idea"
version = "1.2"


repositories {
  maven("https://maven.aliyun.com/repository/public")
  maven("https://maven.aliyun.com/repository/gradle-plugin")
  mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
  version.set("2023.2.6")
  type.set("IC") // Target IDE Platform
  plugins.set(listOf("com.intellij.java", "org.jetbrains.kotlin"))
}

dependencies {
  implementation("com.google.code.gson:gson:2.8.9")
  implementation("org.jgrapht:jgrapht-core:1.5.2")
  implementation("org.jgrapht:jgrapht-ext:1.5.2")
  implementation("org.jgrapht:jgrapht-io:1.5.2")
  implementation("org.tinyjee.jgraphx:jgraphx:3.4.1.3")
  implementation("com.github.javaparser:javaparser-core:3.26.0")
  implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")
  implementation("com.github.vlsi.mxgraph:jgraphx:4.2.2")
}


tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
  }

  patchPluginXml {
    sinceBuild.set("232")
//    untilBuild.set("242.*")
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  buildPlugin {
//    archiveFileName.set("flow-${version}.zip")
    doLast {
      project.copy {
        from("build/distributions")
        include("*.zip")
        into("distributions")
      }
    }
  }

  publishPlugin {
    token.set(System.getenv("PLUGIN_PUBLISH_TOKEN"))
    channels.set(listOf(if (version.toString().endsWith("-SNAPSHOT")) "beta" else "stable"))
  }
}

