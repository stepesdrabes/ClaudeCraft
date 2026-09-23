plugins {
    id("dev.kikugie.loom-back-compat")
    id("claudecraft")
}

val platform = project(":platform")
val modId = property("mod.id") as String
val requiredJava = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    sc.current.parsed >= "1.18" -> JavaVersion.VERSION_17
    sc.current.parsed >= "1.17" -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

val fabricLoader = property("deps.fabric_loader") as String
val fabricApiVersion = property("deps.fabric_api") as String
val keyModule = if (sc.current.parsed >= "26.1") "fabric-key-mapping-api-v1" else "fabric-key-binding-api-v1"
val modProperties = mapOf(
    "key_module" to keyModule,
    "id" to modId,
    "name" to property("mod.name") as String,
    "version" to property("mod.version") as String,
    "minecraft" to property("mod.mc_compat") as String,
    "java" to requiredJava.majorVersion)

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "$modId-fabric"

sourceSets.main {
    java.srcDirs(rootProject.files("agent/api/src/main/java", "agent/mcp/src/main/java", "agent/claude-code/src/main/java", "core/src/main/java"))
}

dependencies {
    fun fabricApi(vararg modules: String) {
        for (module in modules) modImplementation(fabricApi.module(module, fabricApiVersion))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:$fabricLoader")
    fabricApi("fabric-api-base", "fabric-lifecycle-events-v1", "fabric-rendering-v1", keyModule)
}

loom {
    fabricModJsonPath = platform.file("src/main/resources/fabric.mod.json")
    runConfigs.all {
        runDirectory = rootProject.file("run")
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
        filesMatching("fabric.mod.json") { expand(modProperties) }
        exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml", "pack.mcmeta")
    }

    register<PrismInstance>("prism") {
        val minecraft = sc.current.version
        instanceName = "ClaudeCraft $minecraft Fabric"
        components = mapOf(
            "net.minecraft" to minecraft,
            "net.fabricmc.intermediary" to minecraft,
            "net.fabricmc.fabric-loader" to fabricLoader)
        mods.from(loomx.modJar.flatMap { it.archiveFile })
        downloads = listOf("modrinth:fabric-api:$fabricApiVersion")
    }
}
