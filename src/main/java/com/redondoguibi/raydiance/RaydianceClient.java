package com.redondoguibi.raydiance;

import com.redondoguibi.raydiance.client.commands.RaydianceCommands;
import com.redondoguibi.raydiance.client.render.VisualPostProcessor;
import com.redondoguibi.raydiance.client.render.FogController;
import com.redondoguibi.raydiance.compat.direct.DirectShaderBridge;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import com.redondoguibi.raydiance.client.gui.RaydianceScreen;
import com.redondoguibi.raydiance.client.profile.DynamicProfiles;
import com.redondoguibi.raydiance.client.render.TerrainEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** All rendering hooks live on the physical client; dedicated servers need no graphical code. */
@Mod(value = Raydiance.MODID, dist = Dist.CLIENT)
public final class RaydianceClient {
    private static final KeyMapping MENU = new KeyMapping("key.raydiance.menu",GLFW.GLFW_KEY_F8,"key.categories.raydiance");
    public RaydianceClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new RaydianceScreen(parent));
        container.getEventBus().addListener(this::reloadListeners);
        container.getEventBus().addListener(TerrainEffects::register);
        container.getEventBus().addListener(com.redondoguibi.raydiance.client.render.ShadowMapRenderer::register);
        container.getEventBus().addListener((RegisterKeyMappingsEvent event) -> event.register(MENU));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            DynamicProfiles.tick();
            TerrainEffects.tick();
            if(Minecraft.getInstance().level==null)VisualPostProcessor.idle();
            while(MENU.consumeClick()) Minecraft.getInstance().setScreen(new RaydianceScreen(Minecraft.getInstance().screen));
        });
        NeoForge.EVENT_BUS.addListener(RaydianceCommands::register);
        NeoForge.EVENT_BUS.addListener(DirectShaderBridge::onTick);
        NeoForge.EVENT_BUS.addListener(FogController::distance);
        NeoForge.EVENT_BUS.addListener(FogController::color);
        Raydiance.LOGGER.info("Raydiance initialized: standalone OpenGL renderer, optional Direct adapter.");
    }

    private void reloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> VisualPostProcessor.reload());
    }
}
