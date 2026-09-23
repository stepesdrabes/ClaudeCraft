package dev.claudecraft.legacy;

import dev.claudecraft.core.ui.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class LegacyTextures {
    private static final int CAPACITY = 64;
    private static final Map<Integer, ResourceLocation> LOADED = new LinkedHashMap<>(16, 0.75f, true);

    private LegacyTextures() {
    }

    static ResourceLocation get(Image image) {
        ResourceLocation id = LOADED.get(image.id());
        if (id != null) return id;
        TextureManager manager = Minecraft.getMinecraft().getTextureManager();
        DynamicTexture texture = new DynamicTexture(image.width(), image.height());
        System.arraycopy(image.argb(), 0, texture.getTextureData(), 0, image.argb().length);
        texture.updateDynamicTexture();
        GlStateManager.bindTexture(texture.getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        id = manager.getDynamicTextureLocation(LegacyClient.MOD_ID + "_" + image.id(), texture);
        LOADED.put(image.id(), id);
        if (LOADED.size() > CAPACITY) {
            Iterator<ResourceLocation> eldest = LOADED.values().iterator();
            ResourceLocation evicted = eldest.next();
            eldest.remove();
            manager.deleteTexture(evicted);
        }
        return id;
    }

    static Image capture() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Framebuffer framebuffer = minecraft.getFramebuffer();
        boolean fbo = OpenGlHelper.isFramebufferEnabled();
        int stride = fbo ? framebuffer.framebufferTextureWidth : minecraft.displayWidth;
        int rows = fbo ? framebuffer.framebufferTextureHeight : minecraft.displayHeight;
        int width = fbo ? framebuffer.framebufferWidth : stride;
        int height = fbo ? framebuffer.framebufferHeight : rows;
        IntBuffer buffer = BufferUtils.createIntBuffer(stride * rows);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        if (fbo) {
            GlStateManager.bindTexture(framebuffer.framebufferTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        } else {
            GL11.glReadPixels(0, 0, stride, rows, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);
        }
        int[] pixels = new int[stride * rows];
        buffer.get(pixels);
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) argb[y * width + x] = pixels[(height - 1 - y) * stride + x] | 0xFF000000;
        }
        return new Image(width, height, argb);
    }
}
