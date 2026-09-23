pluginManagement {
    includeBuild("../build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.wagyourtail.xyz/releases") { name = "WagYourTail" }
        maven("https://maven.wagyourtail.xyz/snapshots") { name = "WagYourTail Snapshots" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "claudecraft-legacy"

stonecutter {
    create(rootProject) {
        for (minecraft in listOf("1.8.9", "1.12.2")) {
            for (loader in listOf("forge", "legacyfabric")) version("$minecraft-$loader", minecraft).buildscript("build.node.gradle.kts")
        }
        vcsVersion = "1.8.9-forge"
    }
}
