rootProject.name = "feedless"

include("packages:app-web")
include("packages:app-cli")
include("packages:client-lib")
include("packages:server-core")
include("packages:agent")

buildscript {
  repositories {
    gradlePluginPortal()
  }
  dependencies {
    val gradleNodePluginVersion = "3.1.0"
    classpath ("com.github.node-gradle:gradle-node-plugin:$gradleNodePluginVersion")
  }
}

// https://docs.gradle.org/current/userguide/build_cache.html
buildCache {
  local {
    directory = File(rootDir, "build-cache")
    removeUnusedEntriesAfterDays = 30
  }
}

