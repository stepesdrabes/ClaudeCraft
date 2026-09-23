plugins {
    id("net.neoforged.moddev") version "2.0.147"
    id("minecraft-artifacts-mutex")
    id("claudecraft")
}

val modId = property("mod.id") as String
val neoForgeVersion = property("deps.neoforge") as String
val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}
val modProperties = mapOf(
    "id" to modId,
    "name" to property("mod.name") as String,
    "version" to property("mod.version") as String,
    "minecraft" to property("mod.mc_compat") as String)

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "$modId-neoforge"

sourceSets.main {
    java.srcDirs(rootProject.files("agent/api/src/main/java", "agent/mcp/src/main/java", "agent/claude-code/src/main/java", "core/src/main/java"))
    resources.srcDirs(rootProject.files("core/src/main/resources"))
}

neoForge {
    version = neoForgeVersion
    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run")
        }
    }
    mods {
        register(modId) { sourceSet(sourceSets.main.get()) }
    }
}

java {
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks {
    processResources {
        inputs.properties(modProperties)
        filesMatching("META-INF/neoforge.mods.toml") { expand(modProperties) }
        exclude("fabric.mod.json", "META-INF/mods.toml", "pack.mcmeta")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<PrismInstance>("prism") {
        instanceName = "ClaudeCraft ${sc.current.version} NeoForge"
        components = mapOf("net.minecraft" to sc.current.version, "net.neoforged" to neoForgeVersion)
        mods.from(jar.flatMap { it.archiveFile })
    }
}
