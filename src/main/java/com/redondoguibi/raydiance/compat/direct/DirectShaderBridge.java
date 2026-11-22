package com.redondoguibi.raydiance.compat.direct;

import com.redondoguibi.raydiance.Raydiance;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** Links Direct only when a shaderpack can actually use it. */
public final class DirectShaderBridge implements AutoCloseable {
    private static final String PASS_PREFIX = "raydiance.pack.";
    private final Object service;
    private final Class<?> passType;
    private final Method registerPass, unregisterPass, passFailure, geometry, geometryFailure, world, worldFailure;
    private final Method replacesVanilla; // Direct API 1.7+, null on older versions
    private boolean vanillaReplaced;
    private final String apiVersion;

    /** @return whether the installed Direct API is at least {@code major.minor} */
    public boolean apiAtLeast(int major, int minor) {
        String[] parts = apiVersion.split("[.]");
        try {
            int a = Integer.parseInt(parts[0]), b = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return a > major || (a == major && b >= minor);
        } catch (NumberFormatException exception) {
            return false;
        }
    }
    private final List<String> registered = new ArrayList<>();
    private boolean geometrySet;
    private static String status = "";

    public DirectShaderBridge() throws ReflectiveOperationException {
        var type = Class.forName("com.redondoguibi.direct.api.DirectRenderService");
        passType = Class.forName("com.redondoguibi.direct.api.DirectRenderPass");
        service = type.getMethod("getInstance").invoke(null);
        registerPass = type.getMethod("registerPass", passType);
        unregisterPass = type.getMethod("unregisterPass", String.class);
        passFailure = type.getMethod("passFailure", String.class);
        geometry = type.getMethod("shaderPackGeometry", String.class, float[].class, boolean.class, boolean.class, boolean.class);
        geometryFailure = type.getMethod("shaderPackGeometryFailure");
        world = type.getMethod("renderWorld", String[].class, float[].class);
        worldFailure = type.getMethod("worldFailure");
        String version = "0";
        try { version = (String) type.getField("API_VERSION").get(null); } catch (ReflectiveOperationException ignored) {}
        apiVersion = version;
        Method replaces = null;
        try { replaces = type.getMethod("worldReplacesVanilla", boolean.class, boolean.class); } catch (NoSuchMethodException ignored) {}
        replacesVanilla = replaces;
    }

    /** Skips the vanilla sky/clouds while the pack's world passes draw them; ignored before Direct API 1.7. */
    public void replaceVanilla(boolean sky, boolean clouds) throws ReflectiveOperationException {
        if (replacesVanilla == null || (!sky && !clouds && !vanillaReplaced)) return;
        replacesVanilla.invoke(service, sky, clouds);
        vanillaReplaced = sky || clouds;
    }

    public void registerTerrain(String[] sources, float[] frame) throws ReflectiveOperationException {
        try {
            for (int index = 0; index < sources.length; index++) {
                String id = PASS_PREFIX + index;
                String source = sources[index];
                int order = 500 + index;
                var pass = Proxy.newProxyInstance(passType.getClassLoader(), new Class<?>[]{passType},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "id" -> id;
                            case "pixelShaderSource" -> source;
                            case "order" -> order;
                            case "uniforms" -> frame;
                            case "toString" -> id;
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
                registerPass.invoke(service, pass);
                registered.add(id);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public void geometry(String source, float[] options, boolean translucent, boolean sceneInputs, boolean shadows) throws ReflectiveOperationException {
        if (source == null) return;
        geometry.invoke(service, source, options, translucent, sceneInputs, shadows);
        geometrySet = true;
    }

    public String terrainFailure() throws ReflectiveOperationException {
        if (geometrySet) {
            String reason = (String) geometryFailure.invoke(service);
            if (!reason.isEmpty()) return reason;
        }
        for (String id : registered) {
            String reason = (String) passFailure.invoke(service, id);
            if (!reason.isEmpty()) return reason;
        }
        return "";
    }

    public boolean renderWorld(String[] sources, float[] uniforms) throws ReflectiveOperationException {
        return (Boolean) world.invoke(service, sources, uniforms);
    }

    public String worldFailure() throws ReflectiveOperationException {
        return (String) worldFailure.invoke(service);
    }

    public static String status() { return status; }
    public static void status(String latest) { status = latest; }

    @Override
    public void close() {
        try {
            for (String id : registered) unregisterPass.invoke(service, id);
            if (geometrySet) geometry.invoke(service, "", new float[32], false, false, false);
            if (vanillaReplaced) replacesVanilla.invoke(service, false, false);
        } catch (ReflectiveOperationException | LinkageError exception) {
            Raydiance.LOGGER.error("Cannot release Direct shaderpack programs", exception);
        } finally {
            registered.clear();
            geometrySet = false;
            vanillaReplaced = false;
        }
    }
}
