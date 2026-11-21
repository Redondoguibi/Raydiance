package com.redondoguibi.raydiance.compat.direct;

import com.redondoguibi.raydiance.Raydiance;
import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.client.shaders.ShaderPackFiles;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Loads optional custom HLSL fullscreen passes through Direct's documented API.
 * No Direct type is linked by Raydiance: either mod can launch on its own.
 */
public final class DirectShaderBridge {
    private static final String API = "com.redondoguibi.direct.api.DirectRenderService";
    private static final String PASS = "com.redondoguibi.direct.api.DirectRenderPass";
    private static final String PASS_ID = "raydiance.shaderpack";
    private static int tick;
    private static String activeName = ShaderPackFiles.NONE;
    private static String state = "No Direct shader is selected.";
    private static boolean forceRefresh = true;
    private static boolean geometryActive;
    private static boolean geometryRegistered;
    private static String geometryState="";

    private DirectShaderBridge() {
    }

    public static void onTick(ClientTickEvent.Post event) {
        synchronizeGeometry();
        if (++tick >= 40 || forceRefresh || ((!Config.ENABLE_DIRECT_INTEGRATION.get() || !Config.ENABLE_EFFECTS.get()) && !ShaderPackFiles.NONE.equals(activeName))) {
            tick = 0;
            synchronize();
        }
    }

    public static void refresh() {
        forceRefresh = true;
    }

    public static String status() {
        return state + ("none".equals(activeName) ? "" : " Registered: " + activeName)+geometryState;
    }
    public static boolean nativeGeometryActive(){return geometryActive;}
    private static void synchronizeGeometry() {
        var caps = DirectCompatibility.probe();
        if (!caps.installed()) { geometryActive = false; geometryState = ""; return; }
        try {
            Class<?> serviceType = Class.forName(API);
            Object service = serviceType.getMethod("getInstance").invoke(null);
            // running(): Effects or Direct integration off releases every geometry/material/fog request.
            // Geometry, materials and fog follow the pack only while it actually runs on Direct DX12.
            if (!com.redondoguibi.raydiance.client.pipeline.PipelineManager.directActive() || !caps.apiCompatible() || !caps.nativeTerrain()
                    || net.minecraft.client.Minecraft.getInstance().level == null) {
                releaseGeometry();
                geometryState = "";
                return;
            }
            var features = (java.util.Set<?>) serviceType.getMethod("terrainFeatures").invoke(service);
            if (!features.contains("world-post-v1") || !features.contains("shadows") || !features.contains("water")) {
                releaseGeometry();
                geometryState = " Shaderpacks require Direct API 1.5 world-post support.";
                return;
            }

            Class<?> settings = Class.forName("com.redondoguibi.direct.api.DirectTerrainEffects");
            var values = settings.getConstructor(float.class, float.class, float.class, float.class, int.class).newInstance(
                    (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("contactShadows"),
                    (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("water"),
                    (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("reflections"),
                    (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("wind"), com.redondoguibi.raydiance.client.pipeline.PipelineManager.quality());
            boolean materialSupport=features.contains("material-lighting");
            if(materialSupport){
                Class<?> materialType=Class.forName("com.redondoguibi.direct.api.DirectMaterialEffects");
                var materialValues=materialType.getConstructor(float.class,float.class,float.class,float.class).newInstance(
                        (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("materialSpecular"),
                        (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("materialWetness"),
                        (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("materialEmissive"),
                        (float) com.redondoguibi.raydiance.client.profile.DynamicProfiles.value("waterAbsorption"));
                serviceType.getMethod("requestTerrainEffects",String.class,settings,materialType).invoke(service,"raydiance",values,materialValues);
            }else{
                serviceType.getMethod("requestTerrainEffects", String.class, settings).invoke(service, "raydiance", values);
            }
            geometryRegistered = true;
            serviceType.getMethod("shaderFogDistance",float.class).invoke(service,(float)com.redondoguibi.raydiance.client.pipeline.PipelineManager.value("fogDistance"));
            geometryActive = "raydiance".equals(serviceType.getMethod("terrainEffectOwner").invoke(service));
            geometryState = geometryActive ? (materialSupport?" Geometry and materials: DX12.":" Geometry: DX12; materials require Direct API 1.4.") : " Geometry controlled by another extension.";
        } catch (NoSuchMethodException | ClassNotFoundException ex) {
            releaseGeometry();
            geometryState = " Shaderpacks require Direct API 1.5.";
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            releaseGeometry();
            geometryState = " Geometry error: " + rootMessage(ex);
        }
    }

    private static void releaseGeometry() {
        geometryActive = false;
        if (!geometryRegistered) return;
        try {
            Class<?> type = Class.forName(API);
            Object service = type.getMethod("getInstance").invoke(null);
            type.getMethod("releaseTerrainEffects", String.class).invoke(service, "raydiance");
            try{type.getMethod("shaderFogDistance",float.class).invoke(service,1f);}catch(NoSuchMethodException ignored){/* Older Direct has no shader fog request to release. */}
            geometryRegistered = false;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            // Retain the registration flag so the next tick retries the cleanup.
            geometryState = " Cannot release DX12 effects: " + rootMessage(ex);
        }
    }

    private static void synchronize() {
        boolean forced = forceRefresh;
        forceRefresh = false;
        String requested = ShaderPackFiles.selected();

        // Do not force-load Direct when absent. If disabled while a pass was active,
        // unregister our pass to prevent an invisible effect from continuing to run.
        DirectCompatibility.Status capabilities = DirectCompatibility.probe();
        if (!capabilities.enabled() || !capabilities.apiCompatible() || !Config.ENABLE_EFFECTS.get()) {
            if (!ShaderPackFiles.NONE.equals(activeName) && capabilities.installed()) {
                unregister();
            }
            state = "Direct integration not available; standalone Raydiance remains active.";
            return;
        }

        try {
            Class<?> serviceType = Class.forName(API);
            Object service = serviceType.getMethod("getInstance").invoke(null);
            if (ShaderPackFiles.NONE.equals(requested) && ShaderPackFiles.NONE.equals(activeName) && !forced) {
                state = "Direct shaderpack pass is disabled.";
                return;
            }
            if (requested.equals(activeName) && !forced) {
                // Direct 1.1 reports native rejection; older API 1.x remains supported.
                try {
                    String failure = (String) serviceType.getMethod("passFailure", String.class)
                            .invoke(service, PASS_ID);
                    state = failure.isEmpty() ? "HLSL pass registered with Direct."
                            : "Direct rejected the HLSL configuration: " + failure
                                    + " Use /raydiance shader reload after correcting the file.";
                } catch (NoSuchMethodException ignored) {
                    // Direct 1.0 has no asynchronous error query.
                }
                return;
            }
            if (ShaderPackFiles.NONE.equals(requested)) {
                serviceType.getMethod("unregisterPass", String.class).invoke(service, PASS_ID);
                activeName = ShaderPackFiles.NONE;
                state = "Direct shaderpack pass is disabled.";
                return;
            }
            if (!capabilities.shaderPasses()) {
                state = "Direct shader-pass backend unavailable. " + capabilities.message();
                return;
            }

            // Validate the file before replacing the previous working pass.
            String source = ShaderPackFiles.read(requested);
            if(source.contains("// raydiance:depth=1")&&!capabilities.depthInput())
                throw new IOException("This shader requires Direct API 1.2 depth input.");
            boolean liveControls=source.contains("// raydiance:uniforms=1");
            Class<?> passType = Class.forName(PASS);
            Object pass = Proxy.newProxyInstance(passType.getClassLoader(), new Class<?>[]{passType},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "id" -> PASS_ID;
                        case "pixelShaderSource" -> source;
                        case "order" -> 500;
                        case "uniforms" -> liveControls?new float[]{Config.DIRECT_DETAIL.get().floatValue()}:new float[0];
                        case "toString" -> "Raydiance shader: " + requested;
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            Method register = serviceType.getMethod("registerPass", passType);
            register.invoke(service, pass);
            activeName = requested;
            state = "HLSL pass registered with Direct (GPU compilation is performed by Direct).";
            Raydiance.LOGGER.info("Registered Raydiance HLSL pass '{}' through Direct", requested);
        } catch (IOException | ReflectiveOperationException | LinkageError | SecurityException exception) {
            state = "Cannot load Raydiance HLSL: " + rootMessage(exception);
            Raydiance.LOGGER.warn("Could not register Raydiance HLSL pass", exception);
        }
    }

    private static void unregister() {
        try {
            Class<?> serviceType = Class.forName(API);
            Object service = serviceType.getMethod("getInstance").invoke(null);
            serviceType.getMethod("unregisterPass", String.class).invoke(service, PASS_ID);
            activeName = ShaderPackFiles.NONE;
        } catch (ReflectiveOperationException | LinkageError | SecurityException exception) {
            Raydiance.LOGGER.warn("Could not unregister Raydiance's Direct pass", exception);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error instanceof InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : error;
        String text = cause.getMessage();
        return cause.getClass().getSimpleName() + (text == null ? "" : ": " + text);
    }
}
