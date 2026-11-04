package com.redondoguibi.raydiance;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Raydiance settings are client-side and never depend on Direct. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLE_DIRECT_INTEGRATION = BUILDER
            .comment("Enable optional integration with Direct's public render-pass API.")
            .translation("raydiance.configuration.enableDirectIntegration")
            .define("enableDirectIntegration", true);

    public static final ModConfigSpec.BooleanValue ENABLE_EFFECTS = BUILDER
            .comment("Enable Raydiance's native OpenGL color grading pass.")
            .translation("raydiance.configuration.enableEffects")
            .define("enableEffects", true);

    public static final ModConfigSpec.DoubleValue SATURATION = option("saturation", 1.0, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue CONTRAST = option("contrast", 1.0, 0.5, 2.0);
    public static final ModConfigSpec.DoubleValue BRIGHTNESS = option("brightness", 1.0, 0.5, 2.0);
    public static final ModConfigSpec.DoubleValue EXPOSURE = option("exposure", 0.0, -2.0, 2.0);
    public static final ModConfigSpec.DoubleValue GAMMA = option("gamma", 1.0, 0.5, 2.0);
    public static final ModConfigSpec.DoubleValue TEMPERATURE = option("temperature", 0.0, -1.0, 1.0);
    public static final ModConfigSpec.DoubleValue VIGNETTE = option("vignette", 0.0, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue STRENGTH = option("strength", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue FOG_DISTANCE = option("fogDistance", 1.0, 0.25, 3.0);
    public static final ModConfigSpec.BooleanValue FOG_CUSTOM_COLOR = BUILDER
            .comment("Override normal air fog color; underwater/lava fog is not modified.")
            .translation("raydiance.configuration.fogCustomColor")
            .define("fogCustomColor", false);
    public static final ModConfigSpec.DoubleValue FOG_RED = option("fogRed", 0.65, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FOG_GREEN = option("fogGreen", 0.75, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue FOG_BLUE = option("fogBlue", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.ConfigValue<String> DIRECT_SHADER = BUILDER
            .comment("Optional Direct-only Raydiance HLSL shader filename, without extension; 'none' disables it.")
            .define("directShader", "none", value -> value instanceof String text
                    && ("none".equals(text) || text.matches("[a-z0-9][a-z0-9_-]{0,47}")));

    public static final ModConfigSpec.BooleanValue DYNAMIC_PROFILES = BUILDER.define("dynamicProfiles", false);
    public static final ModConfigSpec.DoubleValue TRANSITION_SECONDS = option("transitionSeconds", 3, 0, 30);
    public static final ModConfigSpec.DoubleValue BLOOM = option("bloom", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue BLOOM_THRESHOLD = option("bloomThreshold", 0.8, 0.1, 1);
    public static final ModConfigSpec.DoubleValue ATMOSPHERE = option("atmosphere", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue SHADOWS = option("contactShadows", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue WATER = option("water", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue REFLECTIONS = option("reflections", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue WIND = option("wind", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue MATERIAL_SPECULAR = option("materialSpecular", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue MATERIAL_WETNESS = option("materialWetness", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue MATERIAL_EMISSIVE = option("materialEmissive", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue WATER_ABSORPTION = option("waterAbsorption", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue SSAO = option("ssao", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue GOD_RAYS = option("godRays", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue SHARPEN = option("sharpen", 0, 0, 1);
    public static final ModConfigSpec.DoubleValue TONE_MAPPING = option("toneMapping", 0, 0, 1);
    public static final ModConfigSpec.BooleanValue USE_PACK_DEFAULTS = BUILDER.define("usePackDefaults", true);
    public static final ModConfigSpec.DoubleValue DIRECT_DETAIL = option("directDetail", .12, 0, 1);
    public static final ModConfigSpec.BooleanValue FXAA = BUILDER.define("fxaa", false);
    public static final ModConfigSpec.IntValue QUALITY = BUILDER.defineInRange("quality", 1, 0, 2);
    public static final ModConfigSpec.ConfigValue<String> OPENGL_PACK = BUILDER.define("openglPack", "none",
            v -> v instanceof String text && text.matches("none|[a-z0-9][a-z0-9_-]{0,47}"));

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static ModConfigSpec.DoubleValue option(String key, double initial, double min, double max) {
        return BUILDER.comment("Visual post-processing: " + key)
                .translation("raydiance.configuration." + key)
                .defineInRange(key, initial, min, max);
    }

    private Config() {
    }
}
