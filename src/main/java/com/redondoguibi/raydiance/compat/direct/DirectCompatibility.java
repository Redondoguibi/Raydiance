package com.redondoguibi.raydiance.compat.direct;

import com.redondoguibi.raydiance.Config;
import net.neoforged.fml.ModList;

/**
 * Read-only discovery of Direct's public API. No Direct class is linked at
 * compile time or loaded when Direct is absent.
 *
 * This is a capability probe, not an active shader renderer.
 */
public final class DirectCompatibility {
    private static final String DIRECT_MOD_ID = "direct";
    private static final String SERVICE_CLASS = "com.redondoguibi.direct.api.DirectRenderService";

    private DirectCompatibility() {
    }

    public static Status probe() {
        boolean installed = ModList.get().isLoaded(DIRECT_MOD_ID);
        if (!installed) {
            return new Status(false, false, false, false, false, false,
                    "unknown", "Unavailable", "Direct is not installed.");
        }
        // Disabling extension passes must not pretend the native terrain renderer disappeared.
        boolean integrationEnabled=Config.ENABLE_DIRECT_INTEGRATION.get();

        try {
            Class<?> serviceType = Class.forName(SERVICE_CLASS);
            Object service = serviceType.getMethod("getInstance").invoke(null);
            String version = (String) serviceType.getMethod("apiVersion").invoke(service);
            if (!version.startsWith("1.")) {
                return new Status(true, true, false, false, false, false,
                        version, "Unsupported", "Direct's API major version is not supported.");
            }

            Object capabilities = serviceType.getMethod("capabilities").invoke(service);
            Class<?> capsType = capabilities.getClass();
            return new Status(true, integrationEnabled, true,
                    (Boolean) capsType.getMethod("nativeTerrain").invoke(capabilities),
                    safeShaderApi(version) && (Boolean) capsType.getMethod("shaderPasses").invoke(capabilities),
                    (Boolean) capsType.getMethod("depthInput").invoke(capabilities),
                    version,
                    (String) capsType.getMethod("backendName").invoke(capabilities),
                    safeShaderApi(version)?"Direct capabilities detected.":"Direct API 1.1+ is required for shader passes; update Direct.");
        } catch (ReflectiveOperationException | LinkageError | SecurityException | ClassCastException exception) {
            return new Status(true, true, false, false, false, false,
                    "unknown", "Unavailable", "Direct's public API could not be inspected: "
                            + exception.getClass().getSimpleName());
        }
    }

    /**
     * Direct can post-process the completed world: installed, API 1.5 world-post-v1, and replacing terrain.
     * Called once per client tick; never loads Direct classes when it is absent.
     */
    public static boolean worldPostAvailable() {
        if (!ModList.get().isLoaded(DIRECT_MOD_ID)) return false;
        try {
            Class<?> serviceType = Class.forName(SERVICE_CLASS);
            Object service = serviceType.getMethod("getInstance").invoke(null);
            String version = (String) serviceType.getMethod("apiVersion").invoke(service);
            if (!safeShaderApi(version)) return false;
            Object capabilities = serviceType.getMethod("capabilities").invoke(service);
            if (!(Boolean) capabilities.getClass().getMethod("nativeTerrain").invoke(capabilities)) return false;
            var features = (java.util.Set<?>) serviceType.getMethod("terrainFeatures").invoke(service);
            return features.contains("world-post-v1") && features.contains("pack-geometry-v1");
        } catch (ReflectiveOperationException | LinkageError | SecurityException | ClassCastException exception) {
            return false;
        }
    }

    public record Status(
            boolean installed,
            boolean enabled,
            boolean apiCompatible,
            boolean nativeTerrain,
            boolean shaderPasses,
            boolean depthInput,
            String apiVersion,
            String backendName,
            String message) {
    }
    static boolean safeShaderApi(String version){return version.matches("1\\.[1-9][0-9]*\\.[0-9]+");}
}
