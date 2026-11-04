package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.Config;
import java.util.Locale;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Typed access to the persistent client settings, shared by commands and the renderer. */
public final class VisualSettings {
    private VisualSettings() {
    }

    public record Grade(boolean enabled, float saturation, float contrast, float brightness,
                        float exposure, float gamma, float temperature, float vignette, float strength) {
        public boolean isNeutral() {
            return !enabled || strength <= 0.0f ||
                    (saturation == 1.0f && contrast == 1.0f && brightness == 1.0f
                            && exposure == 0.0f && gamma == 1.0f
                            && temperature == 0.0f && vignette == 0.0f);
        }
    }

    public static Grade current() {
        return new Grade(Config.ENABLE_EFFECTS.get(),
                (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("saturation"), (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("contrast"),
                (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("brightness"), (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("exposure"),
                (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("gamma"), (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("temperature"),
                (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("vignette"), (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("strength"));
    }

    public static boolean set(String name, double value) {
        ModConfigSpec.DoubleValue setting = resolve(name);
        if (setting == null || !Double.isFinite(value) || value < bounds(name)[0]
                || value > bounds(name)[1]) {
            return false;
        }
        setting.set(value);
        return true;
    }

    public static String range(String name) {
        ModConfigSpec.DoubleValue setting = resolve(name);
        return setting == null ? "" : bounds(name)[0] + " - " + bounds(name)[1];
    }

    private static double[] bounds(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "saturation" -> new double[] {0, 2};
            case "vignette", "strength" -> new double[] {0, 1};
            case "contrast", "brightness", "gamma" -> new double[] {0.5, 2};
            case "exposure" -> new double[] {-2, 2};
            case "temperature" -> new double[] {-1, 1};
            default -> new double[] {0, 0};
        };
    }

    private static ModConfigSpec.DoubleValue resolve(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "saturation" -> Config.SATURATION;
            case "contrast" -> Config.CONTRAST;
            case "brightness" -> Config.BRIGHTNESS;
            case "exposure" -> Config.EXPOSURE;
            case "gamma" -> Config.GAMMA;
            case "temperature" -> Config.TEMPERATURE;
            case "vignette" -> Config.VIGNETTE;
            case "strength" -> Config.STRENGTH;
            default -> null;
        };
    }

    public static boolean preset(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "default", "vanilla" -> apply(1, 1, 1, 0, 1, 0, 0, 1);
            case "warm" -> apply(1.05, 1.04, 1.03, 0, 1, 0.25, 0, 1);
            case "vibrant" -> apply(1.30, 1.10, 1.0, 0, 1, 0.10, 0.1, 1);
            case "cinematic" -> apply(0.90, 1.15, 1.0, -0.10, 1.03, 0.12, 0.35, 1);
            default -> { return false; }
        }
        Config.ENABLE_EFFECTS.set(true);
        return true;
    }

    private static void apply(double saturation, double contrast, double brightness, double exposure,
                              double gamma, double temperature, double vignette, double strength) {
        Config.SATURATION.set(saturation);
        Config.CONTRAST.set(contrast);
        Config.BRIGHTNESS.set(brightness);
        Config.EXPOSURE.set(exposure);
        Config.GAMMA.set(gamma);
        Config.TEMPERATURE.set(temperature);
        Config.VIGNETTE.set(vignette);
        Config.STRENGTH.set(strength);
    }

    public static String describe() {
        Grade grade = current();
        return String.format(Locale.ROOT,
                "enabled=%s, saturation=%.2f, contrast=%.2f, brightness=%.2f, exposure=%.2f, "
                        + "gamma=%.2f, temperature=%.2f, vignette=%.2f, strength=%.2f",
                grade.enabled(), grade.saturation(), grade.contrast(), grade.brightness(),
                grade.exposure(), grade.gamma(), grade.temperature(), grade.vignette(), grade.strength());
    }
}
