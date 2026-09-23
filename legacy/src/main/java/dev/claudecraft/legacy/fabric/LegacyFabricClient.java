package dev.claudecraft.legacy.fabric;

//? if legacyfabric {
/*import dev.claudecraft.legacy.LegacyClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;

public final class LegacyFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(LegacyClient.openKey());
        FabricLoader loader = FabricLoader.getInstance();
        LegacyClient.start("Legacy Fabric", loader.getGameDir(), loader.getConfigDir());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> LegacyClient.tick());
        HudRenderCallback.EVENT.register((minecraft, tickDelta) -> LegacyClient.renderHud());
    }
}
*///?}
