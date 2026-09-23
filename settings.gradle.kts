pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "claudecraft"

fun module(name: String, dir: String) {
    include(name)
    project(name).projectDir = file(dir)
}

module(":agent-api", "agent/api")
module(":agent-mcp", "agent/mcp")
module(":agent-claude-code", "agent/claude-code")
module(":core", "core")
include(":platform")

stonecutter {
    create("platform") {
        fun target(minecraft: String, vararg loaders: String) {
            for (loader in loaders) version("$minecraft-$loader", minecraft).buildscript("build.$loader.gradle.kts")
        }

        for (minecraft in listOf("26.3", "26.2", "26.1.2", "1.21.11", "1.21.10", "1.21.8", "1.21.5", "1.21.4", "1.21.1",
            "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.17.1", "1.16.5")) target(minecraft, "fabric")
        for (minecraft in listOf("26.3", "26.2", "26.1.2", "1.21.11", "1.21.10", "1.21.8", "1.21.5", "1.21.4", "1.21.1",
            "1.20.6")) target(minecraft, "neoforge")
        for (minecraft in listOf("1.20.1", "1.19.2", "1.18.2")) target(minecraft, "forge")
        vcsVersion = "26.2-fabric"
    }
}
