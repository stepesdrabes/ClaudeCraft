import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import groovy.json.JsonSlurper
import java.net.URI
import java.net.URLEncoder
import java.util.zip.ZipInputStream

@DisableCachingByDefault(because = "Writes into a Prism Launcher instance outside the build")
abstract class PrismInstance : DefaultTask() {
    @get:Input
    abstract val instanceName: Property<String>

    @get:Input
    abstract val components: MapProperty<String, String>

    @get:Input
    @get:Optional
    abstract val template: Property<String>

    @get:InputFiles
    abstract val mods: ConfigurableFileCollection

    @get:Input
    abstract val downloads: ListProperty<String>

    @get:Internal
    abstract val instancesDirectory: Property<File>

    init {
        group = "prism"
        description = "Creates or updates a Prism Launcher instance with this mod"
        instancesDirectory.convention(project.providers.gradleProperty("prism.instances").map(::File).orElse(defaultInstancesDirectory()))
    }

    @TaskAction
    fun deploy() {
        val instance = instancesDirectory.get().resolve(instanceName.get())
        if (!instance.resolve("mmc-pack.json").exists()) create(instance)
        val modsDirectory = instance.resolve("minecraft/mods").apply { mkdirs() }
        modsDirectory.listFiles { file -> file.name.startsWith("claudecraft-") }?.forEach { it.delete() }
        mods.files.filter { it.name.endsWith(".jar") && !it.name.contains("-sources") }
            .forEach { it.copyTo(modsDirectory.resolve(it.name), overwrite = true) }
        downloads.get().forEach { url -> download(url, modsDirectory) }
        logger.lifecycle("Prism instance ready: ${instance.name}")
    }

    private fun create(instance: File) {
        instance.mkdirs()
        if (template.isPresent) unzip(template.get(), instance)
        else instance.resolve("mmc-pack.json").writeText(pack())
        instance.resolve("instance.cfg").writeText(
            "[General]\nConfigVersion=1.2\nInstanceType=OneSix\niconKey=default\nname=${instanceName.get()}\n")
    }

    private fun pack(): String {
        val entries = components.get().entries.joinToString(",\n") { (uid, version) ->
            val important = if (uid == "net.minecraft") ", \"important\": true" else ""
            "    {\"uid\": \"$uid\", \"version\": \"$version\"$important}"
        }
        return "{\n  \"formatVersion\": 1,\n  \"components\": [\n$entries\n  ]\n}\n"
    }

    private fun download(spec: String, directory: File) {
        val url = if (spec.startsWith("modrinth:")) modrinthUrl(spec.removePrefix("modrinth:")) else spec
        val target = directory.resolve(url.substringAfterLast('/').replace("%2B", "+"))
        if (target.exists()) return
        URI(url).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
    }

    @Suppress("UNCHECKED_CAST")
    private fun modrinthUrl(projectAndVersion: String): String {
        val (project, version) = projectAndVersion.split(':', limit = 2)
        val encoded = URLEncoder.encode(version, "UTF-8").replace("+", "%20")
        val api = URI("https://api.modrinth.com/v2/project/$project/version/$encoded").toURL().readText()
        val files = (JsonSlurper().parseText(api) as Map<String, Any>)["files"] as List<Map<String, Any>>
        return (files.firstOrNull { it["primary"] == true } ?: files.first())["url"] as String
    }

    private fun unzip(url: String, directory: File) {
        ZipInputStream(URI(url).toURL().openStream()).use { zip ->
            generateSequence { zip.nextEntry }.filterNot { it.isDirectory || it.name == "instance.cfg" }.forEach { entry ->
                val target = directory.resolve(entry.name).apply { parentFile.mkdirs() }
                target.outputStream().use { zip.copyTo(it) }
            }
        }
    }

    private fun defaultInstancesDirectory(): File {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> File(home, "Library/Application Support/PrismLauncher/instances")
            os.contains("win") -> File(System.getenv("APPDATA"), "PrismLauncher/instances")
            else -> File(home, ".local/share/PrismLauncher/instances")
        }
    }
}
