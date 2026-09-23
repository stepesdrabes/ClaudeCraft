package dev.claudecraft.platform.fabric;

//? if fabric {
import dev.claudecraft.platform.ClaudeCraftClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else
//import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
//?} else
//import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class FabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        //? if >=26.1 {
        KeyMappingHelper.registerKeyMapping(ClaudeCraftClient.openKey());
        //?} else
        //KeyBindingHelper.registerKeyBinding(ClaudeCraftClient.openKey());
        FabricLoader loader = FabricLoader.getInstance();
        ClaudeCraftClient.start("Fabric", loader.getGameDir(), loader.getConfigDir());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> ClaudeCraftClient.tick());
        //? if >=1.21.6 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ClaudeCraftClient.MOD_ID, "status"),
            (graphics, deltaTracker) -> ClaudeCraftClient.renderHud(graphics));
        //?} else
        //HudRenderCallback.EVENT.register((graphics, delta) -> ClaudeCraftClient.renderHud(graphics));
    }
}
//?}
