package com.redondoguibi.raydiance.client.profile;
import com.redondoguibi.raydiance.Config;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import java.util.Map;
public final class SettingsOptions {
    private static final Map<String,DoubleValue> NUMBERS = Map.ofEntries(
        Map.entry("materialSpecular",Config.MATERIAL_SPECULAR),
        Map.entry("materialWetness",Config.MATERIAL_WETNESS),
        Map.entry("materialEmissive",Config.MATERIAL_EMISSIVE),
        Map.entry("waterAbsorption",Config.WATER_ABSORPTION),
        Map.entry("saturation",Config.SATURATION),Map.entry("contrast",Config.CONTRAST),Map.entry("brightness",Config.BRIGHTNESS),
        Map.entry("exposure",Config.EXPOSURE),Map.entry("gamma",Config.GAMMA),Map.entry("temperature",Config.TEMPERATURE),
        Map.entry("vignette",Config.VIGNETTE),Map.entry("strength",Config.STRENGTH),Map.entry("fogDistance",Config.FOG_DISTANCE),
        Map.entry("bloom",Config.BLOOM),Map.entry("bloomThreshold",Config.BLOOM_THRESHOLD),Map.entry("atmosphere",Config.ATMOSPHERE),
        Map.entry("contactShadows",Config.SHADOWS),Map.entry("water",Config.WATER),Map.entry("reflections",Config.REFLECTIONS),Map.entry("wind",Config.WIND),Map.entry("ssao",Config.SSAO),Map.entry("godRays",Config.GOD_RAYS),Map.entry("sharpen",Config.SHARPEN),Map.entry("toneMapping",Config.TONE_MAPPING));
    public static Map<String,DoubleValue> numbers() { return NUMBERS; }
    public static double number(String key) { return numbers().get(key).get(); }
    private SettingsOptions() {}
}
