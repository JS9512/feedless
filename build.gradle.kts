import java.util.*

buildscript {
  repositories {
    gradlePluginPortal()
  }
  dependencies {
    classpath("com.github.node-gradle:gradle-node-plugin:${findProperty("gradleNodePluginVersion")}")
  }
}

plugins {
  id("org.ajoberstar.grgit")
}

val buildDockerAioWeb = tasks.register("buildDockerAioWeb", Exec::class) {
  dependsOn(appWebTask(), serverCoreTask(), agentTask())

  val semver = findProperty("feedlessVersion") as String
  val baseTag = findProperty("dockerImageTag")
  val gitHash = grgit.head().id

  // with web
  commandLine(
    "docker", "build",
    "--build-arg", "APP_VERSION=$semver",
    "--build-arg", "APP_GIT_HASH=$gitHash",
    "--build-arg", "APP_BUILD_TIMESTAMP=${Date().time}",
    "--platform=linux/amd64",
//    "--platform=linux/arm64v8",
//    "-t", "$baseTag:aio",
    "-t", "$baseTag:aio-$gitHash",
    "docker/aio-with-web"
  )
}

val buildDockerAioChromium = tasks.register("buildDockerAioChromium", Exec::class) {
  dependsOn(buildDockerAioWeb)

  val semver = findProperty("feedlessVersion") as String
  val baseTag = findProperty("dockerImageTag")
  val gitHash = grgit.head().id

  // with chromium
  commandLine(
    "docker", "build",
    "--build-arg", "APP_VERSION=$semver",
    "--build-arg", "APP_GIT_HASH=$gitHash",
    "--build-arg", "APP_BUILD_TIMESTAMP=${Date().time}",
    "--platform=linux/amd64",
//    "--platform=linux/arm64v8",
//    "-t", "$baseTag:aio-chromium",
    "-t", "$baseTag:aio-chromium-$gitHash",
    "docker/aio-with-chromium"
  )
}

val prepareTask = tasks.register("prepare") {

}

val buildTask = tasks.register("build") {
  dependsOn(prepareTask, buildDockerAioWeb, buildDockerAioChromium)
}

tasks.register("publish", Exec::class) {
  dependsOn(buildTask)

  val gitHash = grgit.head().id
  val semver = (findProperty("feedlessVersion") as String).split(".")
  val major = semver[0]
  val minor = semver[1]
  val patch = semver[2]
  commandLine("sh", "./scripts/semver-tag-docker-images.sh", gitHash, major, minor, patch)
}

subprojects {
  tasks.register("lintDockerImage", Exec::class) {
    commandLine(
      "sh",
      rootProject.file("lintDockerfile.sh").getAbsolutePath(),
      project.file("Dockerfile").getAbsolutePath()
    )
  }
}

fun appWebTask() = tasks.findByPath("packages:app-web:buildDockerImage")
fun serverCoreTask() = tasks.findByPath("packages:server-core:buildDockerImage")
fun agentTask() = tasks.findByPath("packages:agent:buildDockerImage")
