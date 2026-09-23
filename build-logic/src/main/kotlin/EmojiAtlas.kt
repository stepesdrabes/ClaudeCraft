import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

@DisableCachingByDefault(because = "Downloads Twemoji and Unicode data; the output is committed")
abstract class EmojiAtlas : DefaultTask() {
    @get:Input
    abstract val twemojiVersion: Property<String>

    @get:Input
    abstract val unicodeVersion: Property<String>

    @get:Input
    abstract val cellSize: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "build setup"
        description = "Regenerates the Twemoji atlas used to draw emoji in the panel"
    }

    private class Emoji(val sequences: MutableList<String>, val files: List<String>)

    @TaskAction
    fun generate() {
        System.setProperty("java.awt.headless", "true")
        val version = twemojiVersion.get()
        val pngs = mutableMapOf<String, ByteArray>()
        var license = ByteArray(0)
        ZipInputStream(URI("https://github.com/jdecked/twemoji/archive/refs/tags/v$version.zip").toURL().openStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val name = entry.name.substringAfter('/')
                if (name.startsWith("assets/72x72/") && name.endsWith(".png")) pngs[name.removePrefix("assets/72x72/").removeSuffix(".png")] = zip.readBytes()
                if (name == "LICENSE-GRAPHICS") license = zip.readBytes()
            }
        }
        val emoji = parseEmojiTest(URI("https://www.unicode.org/Public/${unicodeVersion.get()}/emoji/emoji-test.txt").toURL().readText())
            .filter { entry -> entry.files.any { it in pngs } }

        val cell = cellSize.get()
        val pitch = cell + 2
        val columns = 64
        val rows = (emoji.size + columns - 1) / columns
        val atlas = BufferedImage(columns * pitch, rows * pitch, BufferedImage.TYPE_INT_ARGB)
        val graphics = atlas.createGraphics()
        emoji.forEachIndexed { index, entry ->
            val source = ImageIO.read(ByteArrayInputStream(pngs.getValue(entry.files.first { it in pngs })))
            val scaled = source.getScaledInstance(cell, cell, Image.SCALE_AREA_AVERAGING)
            graphics.drawImage(scaled, index % columns * pitch + 1, index / columns * pitch + 1, null)
        }
        graphics.dispose()

        val output = outputDirectory.get().asFile.apply { mkdirs() }
        ImageIO.write(atlas, "png", output.resolve("twemoji.png"))
        output.resolve("twemoji.txt").writeText(buildString {
            append("$cell $columns ${emoji.size}\n")
            emoji.forEach { append(it.sequences.joinToString(" ")).append('\n') }
        })
        output.resolve("LICENSE-TWEMOJI").writeBytes(license)
        logger.lifecycle("Packed ${emoji.size} emoji from Twemoji $version into ${atlas.width}x${atlas.height}")
    }

    private fun parseEmojiTest(text: String): List<Emoji> {
        val emoji = mutableListOf<Emoji>()
        for (line in text.lines()) {
            if (line.isBlank() || line.startsWith("#")) continue
            val codePoints = line.substringBefore(';').trim().split(' ').map { it.toInt(16).toString(16) }
            val status = line.substringAfter(';').substringBefore('#').trim()
            val sequence = codePoints.joinToString("-")
            val withoutSelectors = codePoints.filter { it != "fe0f" }.joinToString("-")
            when (status) {
                "fully-qualified", "component" -> {
                    val files = if ("200d" in codePoints) listOf(sequence, withoutSelectors) else listOf(withoutSelectors)
                    emoji += Emoji(mutableListOf(sequence), files)
                }
                "minimally-qualified" -> emoji.lastOrNull()?.sequences?.add(sequence)
            }
        }
        return emoji
    }
}
