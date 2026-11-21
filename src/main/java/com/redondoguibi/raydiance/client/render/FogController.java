package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.Config;
import java.util.Locale;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Normal air fog customization via the public NeoForge rendering events. */
public final class FogController {
    private FogController() {
    }

    public static void distance(ViewportEvent.RenderFog event) {
        // Direct DX12 packs own terrain fog (shaderFogDistance); OpenGL packs use their fogDistance here.
        if (com.redondoguibi.raydiance.client.pipeline.PipelineManager.directActive() || !Config.ENABLE_EFFECTS.get() || event.getType() != FogType.NONE
                || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        float factor = (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("fogDistance");
        if (factor == 1.0f) {
            return;
        }
        float far = Math.max(0.01f, event.getFarPlaneDistance() * factor);
        float near = Math.min(far - 0.01f, event.getNearPlaneDistance() * factor);
        event.setNearPlaneDistance(near);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }

    public static void color(ViewportEvent.ComputeFogColor event) {
        if (com.redondoguibi.raydiance.client.pipeline.PipelineManager.directActive() || !Config.ENABLE_EFFECTS.get() || !Config.FOG_CUSTOM_COLOR.get()
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        event.setRed(Config.FOG_RED.get().floatValue());
        event.setGreen(Config.FOG_GREEN.get().floatValue());
        event.setBlue(Config.FOG_BLUE.get().floatValue());
    }

    public static boolean setDistance(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.25 || multiplier > 3.0) {
            return false;
        }
        Config.FOG_DISTANCE.set(multiplier);
        return true;
    }

    public static boolean setColor(double red, double green, double blue) {
        if (!component(red) || !component(green) || !component(blue)) {
            return false;
        }
        Config.FOG_RED.set(red);
        Config.FOG_GREEN.set(green);
        Config.FOG_BLUE.set(blue);
        Config.FOG_CUSTOM_COLOR.set(true);
        return true;
    }

    private static boolean component(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    public static void reset() {
        Config.FOG_DISTANCE.set(1.0);
        Config.FOG_CUSTOM_COLOR.set(false);
    }

    public static String describe() {
        return String.format(Locale.ROOT, "Fog: distance=%.2fx, colorOverride=%s, RGB=(%.2f, %.2f, %.2f)",
                Config.FOG_DISTANCE.get(), Config.FOG_CUSTOM_COLOR.get(),
                Config.FOG_RED.get(), Config.FOG_GREEN.get(), Config.FOG_BLUE.get());
    }
}
