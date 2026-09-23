package dev.claudecraft.legacy.forge;

//? if forge {
import dev.claudecraft.legacy.LegacyClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
//? if <1.12
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;

@Mod(modid = LegacyClient.MOD_ID, name = "ClaudeCraft", version = "0.1.0", clientSideOnly = true, acceptedMinecraftVersions = "*")
public final class ForgeMod {
    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientRegistry.registerKeyBinding(LegacyClient.openKey());
        File config = Loader.instance().getConfigDir();
        LegacyClient.start("Forge", config.getParentFile().toPath(), config.toPath());
        MinecraftForge.EVENT_BUS.register(this);
        //? if <1.12
        FMLCommonHandler.instance().bus().register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) LegacyClient.tick();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        //? if >=1.12 {
        /*if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) LegacyClient.renderHud();
        *///?} else
        if (event.type == RenderGameOverlayEvent.ElementType.ALL) LegacyClient.renderHud();
    }
}
//?}
