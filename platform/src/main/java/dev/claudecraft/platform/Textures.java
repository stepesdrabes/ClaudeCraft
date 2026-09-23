package dev.claudecraft.platform;

import com.mojang.blaze3d.platform.NativeImage;
import dev.claudecraft.core.ui.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
//? if >=1.21.11 {
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
//?}

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class Textures {
    private static final int CAPACITY = 64;
    private static final Map<Integer, Identifier> LOADED = new LinkedHashMap<>(16, 0.75f, true);

    private Textures() {
    }

    static Identifier get(Image image) {
        Identifier id = LOADED.get(image.id());
        if (id != null) return id;
        id = id(image.id());
        Minecraft.getInstance().getTextureManager().register(id, texture(image));
        LOADED.put(image.id(), id);
        if (LOADED.size() > CAPACITY) {
            Iterator<Identifier> eldest = LOADED.values().iterator();
            Identifier evicted = eldest.next();
            eldest.remove();
            Minecraft.getInstance().getTextureManager().release(evicted);
        }
        return id;
    }

    private static Identifier id(int image) {
        //? if >=1.21 {
        return Identifier.fromNamespaceAndPath(ClaudeCraftClient.MOD_ID, "dynamic/" + image);
        //?} else
        //return new Identifier(ClaudeCraftClient.MOD_ID, "dynamic/" + image);
    }

    private static DynamicTexture texture(Image image) {
        NativeImage pixels = new NativeImage(NativeImage.Format.RGBA, image.width(), image.height(), false);
        int[] argb = image.argb();
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                //? if >=1.21.2 {
                pixels.setPixel(x, y, argb[y * image.width() + x]);
                //?} else
                //pixels.setPixelRGBA(x, y, swap(argb[y * image.width() + x]));
            }
        }
        //? if >=1.21.11 {
        return new Smooth(pixels);
        //?} elif >=1.21.5 {
        /*DynamicTexture texture = new DynamicTexture(() -> ClaudeCraftClient.MOD_ID, pixels);
        texture.setFilter(true, false);
        return texture;
        *///?} else {
        /*DynamicTexture texture = new DynamicTexture(pixels);
        texture.setFilter(true, false);
        return texture;
        *///?}
    }

    static Image read(NativeImage source) {
        try {
            int width = source.getWidth();
            int height = source.getHeight();
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    //? if >=1.21.2 {
                    argb[y * width + x] = source.getPixel(x, y) | 0xFF000000;
                    //?} else
                    //argb[y * width + x] = swap(source.getPixelRGBA(x, y)) | 0xFF000000;
                }
            }
            return new Image(width, height, argb);
        } finally {
            source.close();
        }
    }

    static int swap(int color) {
        return color & 0xFF00FF00 | (color >> 16) & 0xFF | (color & 0xFF) << 16;
    }
    //? if >=1.21.11 {

    private static final class Smooth extends DynamicTexture {
        Smooth(NativeImage pixels) {
            super(() -> ClaudeCraftClient.MOD_ID, pixels);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }
    //?}
}
