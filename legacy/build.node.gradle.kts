plugins {
    java
    id("xyz.wagyourtail.unimined") version "1.4.1"
    id("claudecraft")
}

val mcVersion = sc.current.version
val loader = sc.current.project.substringAfterLast('-')
val forgeVersion = if (mcVersion == "1.8.9") "11.15.1.2318" else "14.23.5.2864"
val legacyFabricLoader = "0.19.3"
val legacyFabricApi = "modrinth:legacy-fabric-api:1.20.1"
val modId = property("mod.id") as String
val modProperties = mapOf(
    "id" to modId,
    "name" to property("mod.name") as String,
    "version" to property("mod.version") as String,
    "minecraft" to mcVersion)

version = "${property("mod.version")}+$mcVersion"
base.archivesName = "$modId-$loader"

repositories {
    mavenCentral()
    maven("https://maven.legacyfabric.net/") { name = "LegacyFabric" }
}

sourceSets.main {
    java.srcDirs(rootProject.files("../agent/api/src/main/java", "../agent/mcp/src/main/java", "../agent/claude-code/src/main/java", "../core/src/main/java"))
    resources.srcDirs(rootProject.files("../core/src/main/resources"))
}

unimined.minecraft {
    version(mcVersion)
    mappings {
        searge()
        mcp("stable", if (mcVersion == "1.8.9") "22-1.8.9" else "39-1.12")
    }
    if (loader == "forge") {
        minecraftForge { loader(if (mcVersion == "1.8.9") "$forgeVersion-1.8.9" else forgeVersion) }
    } else {
        legacyFabric { loader(legacyFabricLoader) }
        minecraftRemapper.config {
            ignoreFieldDesc(true)
            ignoreConflicts(true)
        }
    }
}

dependencies {
    if (loader == "legacyfabric") "modImplementation"("net.legacyfabric.legacy-fabric-api:legacy-fabric-api:1.13.5+$mcVersion")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.release = 8
        options.encoding = "UTF-8"
    }

    processResources {
        inputs.properties(modProperties)
        filesMatching(listOf("mcmod.info", "fabric.mod.json")) { expand(modProperties) }
        if (loader == "forge") exclude("fabric.mod.json") else exclude("mcmod.info")
        if (mcVersion != "1.8.9") rename("en_US.lang", "en_us.lang")
    }
}

tasks.register<PrismInstance>("prism") {
    mods.from(tasks.named("remapJar"))
    if (loader == "forge") {
        instanceName = "ClaudeCraft $mcVersion Forge"
        components = mapOf("net.minecraft" to mcVersion, "net.minecraftforge" to forgeVersion)
    } else {
        instanceName = "ClaudeCraft $mcVersion Legacy Fabric"
        template = "https://meta.legacyfabric.net/v2/versions/loader/$mcVersion/$legacyFabricLoader/instance/zip"
        downloads = listOf(legacyFabricApi)
    }
}
