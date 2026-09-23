package dev.claudecraft.platform.neoforge;

//? if neoforge {
/*import dev.claudecraft.platform.ClaudeCraftClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ClaudeCraftClient.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            //? if >=1.21.9
            event.registerCategory(ClaudeCraftClient.openKey().getCategory());
            event.register(ClaudeCraftClient.openKey());
        });
        ClaudeCraftClient.start("NeoForge", FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> ClaudeCraftClient.tick());
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, event -> ClaudeCraftClient.renderHud(event.getGuiGraphics()));
    }
}
*///?}
