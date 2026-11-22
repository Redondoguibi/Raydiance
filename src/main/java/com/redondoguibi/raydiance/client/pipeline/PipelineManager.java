package com.redondoguibi.raydiance.client.pipeline;

import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.Raydiance;
import com.redondoguibi.raydiance.client.render.DirectPipeline;
import com.redondoguibi.raydiance.client.render.GlPipeline;
import com.redondoguibi.raydiance.client.render.GlState;
import com.redondoguibi.raydiance.client.shaders.*;
import com.redondoguibi.raydiance.compat.direct.DirectCompatibility;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.Map;

/** Selects either the standalone effects or the selected Direct shaderpack before world draws. */
public final class PipelineManager {
    private static ShaderPipeline pipeline;
    private static String pipelineKey, failedKey, packKey;
    private static PackRepository.Pack pack = PackRepository.NONE;
    private static String packError = "", buildError = "", status = "";
    private static int generation, loadedGeneration = -1;
    private static String loadedId, loadedLanguage;
    private static ResourceKey<Level> loadedDimension;
    private static String standalonePipelineKey = "standalone|0";
    private static Map<String,String> defines = Map.of();
    private static String definesPackKey, nativePipelineKey;
    private static long definesRevision = -1;
    private static boolean announce, directAvailable;

    private PipelineManager() {}

    public static boolean packSelected() { return !"none".equals(Config.OPENGL_PACK.get()); }
    public static boolean shadersRequested() { return BackendSelector.shadersRequested(packSelected(), Config.ENABLE_DIRECT_INTEGRATION.get()); }
    public static boolean standaloneActive() { return BackendSelector.standaloneActive(shadersRequested(), Config.ENABLE_EFFECTS.get()); }
    public static int quality() { return Config.QUALITY.get(); }
    public static boolean fxaa() { return Config.FXAA.get(); }

    public static void tick() {
        directAvailable = shadersRequested() && DirectCompatibility.worldPostAvailable() && !Minecraft.useShaderTransparency();
    }

    private static void ensurePack() {
        var mc = Minecraft.getInstance();
        ResourceKey<Level> dimension = mc.level == null ? Level.OVERWORLD : mc.level.dimension();
        String language = mc.getLanguageManager().getSelected();
        String id = Config.OPENGL_PACK.get();
        if (generation == loadedGeneration && id.equals(loadedId) && dimension.equals(loadedDimension)
                && language.equals(loadedLanguage)) return;
        loadedGeneration = generation;
        loadedId = id;
        loadedDimension = dimension;
        loadedLanguage = language;
        String dimensionName = dimension.location().toString();
        packKey = id + "|" + dimensionName + "|" + language + "|" + generation;
        try {
            pack = PackRepository.load(id, new PackRepository.LoadContext(dimensionName, language));
            if (!pack.none()) {
                if (!pack.canRunDirect()) throw new java.io.IOException("Shaderpack requires a format 4 manifest and HLSL programs");
                PackOptionStore.live().prepare(pack);
            }
            packError = "";
        } catch (Exception ex) {
            pack = PackRepository.NONE;
            packError = ShaderErrors.summary(ex.getMessage(), 4);
            Raydiance.LOGGER.warn("Cannot load shaderpack {}", id, ex);
        }
        PackRepository.accept(pack);
    }

    public static PackRepository.Pack pack() { ensurePack(); return pack; }
    public static boolean packReady() { return !pack().none(); }
    public static String reason() { return status; }

    public static void prepareFrame(float partialTicks) {
        ensurePack();
        boolean nativePack = shadersRequested();
        String blocked = nativePack ? packBlocker() : standaloneActive() ? "" : "Standalone disabled";
        if (!blocked.isEmpty()) {
            close();
            status = blocked;
            return;
        }
        var target = nativePack ? pack : PackRepository.NONE;
        if (nativePack) {
            var store = PackOptionStore.live();
            if (!packKey.equals(definesPackKey) || definesRevision != store.revision()) {
                defines = store.defineValues(pack);
                definesPackKey = packKey;
                definesRevision = store.revision();
                nativePipelineKey = packKey + "|" + defines;
            }
        }
        String key = nativePack ? nativePipelineKey : standalonePipelineKey;
        if (!key.equals(pipelineKey) && !key.equals(failedKey)) {
            close();
            try {
                var mc = Minecraft.getInstance();
                if (nativePack) {
                    var macros = ShaderMacros.standard("dx12", 1, mc.level.dimension().location().toString(), target.format());
                    pipeline = new DirectPipeline(target, macros, defines);
                } else {
                    try (GlState state = new GlState()) {
                        pipeline = new GlPipeline();
                    }
                }
                pipelineKey = key;
                failedKey = null;
                buildError = "";
                if (announce) {
                    announce = false;
                    mc.gui.setOverlayMessage(Component.literal(nativePack ? target.title() : "Raydiance Standalone"), false);
                }
            } catch (Exception | LinkageError ex) {
                failedKey = key;
                buildError = ShaderErrors.summary(ex.getMessage(), 5);
                Raydiance.LOGGER.error("Cannot prepare Raydiance pipeline", ex);
            }
        }
        if (pipeline instanceof DirectPipeline direct) direct.prepareTerrain(partialTicks);
        status = pipeline == null ? buildError : pipeline.status();
    }

    private static String packBlocker() {
        if (pack.none()) return packError.isEmpty() ? "Shaderpack unavailable" : packError;
        if (!pack.canRunDirect()) return "This shaderpack needs a format 4 manifest and HLSL programs";
        if (!directAvailable) return "Requires Direct 1.8+, DX12 terrain replacement and Fast/Fancy graphics";
        return "";
    }

    public static void render(float partialTicks) {
        if (pipeline == null) return;
        pipeline.render(partialTicks);
        status = pipeline.status();
        if (pipeline.rejected()) {
            failedKey = pipelineKey;
            buildError = status;
            close();
        }
    }

    private static void close() {
        if (pipeline == null) return;
        if (pipeline.kind() == ShaderPipeline.Kind.DIRECT) pipeline.close();
        else try (GlState state = new GlState()) { pipeline.close(); }
        pipeline = null;
        pipelineKey = null;
    }

    public static void idle() { close(); }
    public static void releaseTargets() {
        if (pipeline != null && pipeline.kind() == ShaderPipeline.Kind.OPENGL)
            try (GlState state = new GlState()) { pipeline.releaseTargets(); }
    }
    public static void reload(boolean announce) {
        generation++;
        standalonePipelineKey = "standalone|" + generation;
        failedKey = null;
        buildError = "";
        PipelineManager.announce = announce;
    }
    public static void shutdown() { close(); }
    public static String status() {
        ensurePack();
        return "Pack: " + (pack.none() ? Config.OPENGL_PACK.get() : pack.title())
                + "\n" + (packError.isEmpty() ? status : packError);
    }
}
