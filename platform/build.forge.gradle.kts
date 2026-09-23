plugins {
    id("net.neoforged.moddev.legacyforge") version "2.0.147"
    id("minecraft-artifacts-mutex")
    id("claudecraft")
}

val modId = property("mod.id") as String
val forgeVersion = property("deps.forge") as String
val requiredJava = when {
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    else -> JavaVersion.VERSION_16
}
val modProperties = mapOf(
    "id" to modId,
    "name" to property("mod.name") as String,
    "version" to property("mod.version") as String,
    "minecraft" to property("mod.mc_compat") as String)

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "$modId-forge"

sourceSets.main {
    java.srcDirs(rootProject.files("agent/api/src/main/java", "agent/mcp/src/main/java", "agent/claude-code/src/main/java", "core/src/main/java"))
}

legacyForge {
    version = "${sc.current.version}-$forgeVersion"
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
        filesMatching("META-INF/mods.toml") { expand(modProperties) }
        exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    register<PrismInstance>("prism") {
        instanceName = "ClaudeCraft ${sc.current.version} Forge"
        components = mapOf("net.minecraft" to sc.current.version, "net.minecraftforge" to forgeVersion)
        mods.from(named("reobfJar"))
    }
}
