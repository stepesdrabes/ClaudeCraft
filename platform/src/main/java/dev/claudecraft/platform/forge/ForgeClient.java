package dev.claudecraft.platform.forge;

//? if forge {
/*import dev.claudecraft.platform.ClaudeCraftClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
//? if >=1.19 {
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
//?} else {
/^import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
^///?}

@Mod(ClaudeCraftClient.MOD_ID)
public final class ForgeClient {
    public ForgeClient() {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        //? if >=1.19 {
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(ClaudeCraftClient.openKey()));
        //?} else
        //modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> ClientRegistry.registerKeyBinding(ClaudeCraftClient.openKey())));
        ClaudeCraftClient.start("Forge", FMLPaths.GAMEDIR.get(), FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) ClaudeCraftClient.tick();
        });
        //? if >=1.20 {
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> ClaudeCraftClient.renderHud(event.getGuiGraphics()));
        //?} elif >=1.19 {
        /^MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> ClaudeCraftClient.renderHud(event.getPoseStack()));
        ^///?} else {
        /^MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post event) -> {
            if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) ClaudeCraftClient.renderHud(event.getMatrixStack());
        });
        ^///?}
    }
}
*///?}
