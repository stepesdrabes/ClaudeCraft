plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2-fabric"

stonecutter parameters {
    val (_, loader) = current.project.split('-', limit = 2)
    properties { tags(current.version, loader) }
    constants { match(loader, "fabric", "neoforge", "forge") }
    swaps["minecraft"] = "\"${current.version}\";"
    replacements {
        string(current.parsed >= "1.21.11") { replace("ResourceLocation", "Identifier") }
        string(current.parsed >= "26.1") { replace("GuiGraphics;", "GuiGraphicsExtractor;") }
        string(current.parsed >= "26.1") { replace("GuiGraphics graphics", "GuiGraphicsExtractor graphics") }
        string(current.parsed >= "26.3") { replace("com.mojang.blaze3d.textures.", "com.mojang.renderpearl.api.textures.") }
    }
}
