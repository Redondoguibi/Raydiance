package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.client.pipeline.ShaderPipeline;
import com.redondoguibi.raydiance.client.shaders.*;
import com.redondoguibi.raydiance.compat.direct.DirectShaderBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;
import java.util.List;
import java.util.Map;

/** Runs the pack's HLSL at the terrain and complete-world stages of Direct. */
public final class DirectPipeline implements ShaderPipeline {
    private static final int FRAME_FLOATS = 252;
    private final PackRepository.Pack pack;
    private final DirectShaderBridge direct;
    private final String[] worldPrograms;
    private String[] world = {};
    private long optionsRevision = -1;
    private final boolean hasTerrainPasses;
    private final String geometry;
    private final PackRepository.Geometry geometrySpec;
    private final List<ShaderOption> options;
    private final float[] frame = new float[FRAME_FLOATS];
    private final float[] geometryValues = new float[32];
    private float[] worldUniforms = {};
    private final Vector3f sun = new Vector3f();
    private String status = "Waiting for Direct DX12";
    private boolean rejected;

    public DirectPipeline(PackRepository.Pack pack, Map<String, String> macros, Map<String, String> defines)
            throws ReflectiveOperationException {
        this.pack = pack;
        options = pack.uniformOptions();
        var programs = pack.nativePrograms();
        String[] terrain = compileInputs(programs.terrain(), macros, defines);
        worldPrograms = programs.world().stream().map(pass -> worldHeader(pass, programs)
                + prepare(pass.source(), pass.file(), macros, defines, false)).toArray(String[]::new);
        hasTerrainPasses = terrain.length != 0;
        geometrySpec = programs.geometry();
        geometry = geometrySpec == null ? null : prepare(geometrySpec.source(), geometrySpec.file(), macros, defines, true);
        direct = new DirectShaderBridge();
        if ((programs.usesGraph() || programs.world().size() > 8 || !programs.replaceVanilla().isEmpty()) && !direct.apiAtLeast(1, 7))
            throw new IllegalStateException("This shaderpack uses world buffers, compute passes or vanilla replacement: install Direct 1.9 (API 1.7) or newer");
        direct.registerTerrain(terrain, frame);
        direct.replaceVanilla(programs.replaceVanilla().contains("sky"), programs.replaceVanilla().contains("clouds"));
    }

    /**
     * Direct world-graph directives and declarations for one world pass (Direct 1.9 / API 1.7). Named buffers
     * become {@code Texture2D<float4> name} (t4+), {@code nameHistory} (t12+), {@code nameSize} and, in a compute
     * pass that writes them, {@code RWTexture2D<float4> nameOut}. The HDR directive stays first.
     */
    static String worldHeader(PackRepository.NativePass pass, PackRepository.NativePrograms programs) {
        StringBuilder header = new StringBuilder();
        if (pass.hdr()) header.append("// direct:target=rgba16f\n");
        if (!programs.usesGraph()) return header.toString();
        var buffers = programs.buffers();
        for (var buffer : buffers) {
            header.append("// direct:buffer ").append(buffer.index()).append(' ').append(buffer.format());
            if (buffer.scale() != 1) header.append(" scale=").append(buffer.scale());
            if (buffer.history()) header.append(" history");
            if (buffer.clear()) header.append(" clear");
            header.append('\n');
        }
        if (!pass.outputs().isEmpty()) {
            header.append("// direct:outputs ");
            for (int i = 0; i < pass.outputs().size(); i++) {
                String output = pass.outputs().get(i);
                if (i > 0) header.append(',');
                header.append(output.equals("color") ? "color"
                        : Integer.toString(buffers.stream().filter(b -> b.name().equals(output)).findFirst().orElseThrow().index()));
            }
            header.append('\n');
        }
        if (pass.compute()) header.append("// direct:compute\n");
        if (programs.terrainDepth()) header.append("// direct:terrain-depth\n");
        header.append("cbuffer RaydianceGraph : register(b1) { float4 DirectTarget; float4 DirectBufferSize[8]; };\n");
        header.append("SamplerState RaydiancePointClamp : register(s1);\n");
        if (programs.terrainDepth()) header.append("Texture2D<float> RaydianceTerrainDepth : register(t3);\n");
        for (var buffer : buffers) {
            String name = buffer.name();
            int i = buffer.index();
            header.append("Texture2D<float4> ").append(name).append(" : register(t").append(4 + i).append(");\n");
            header.append("Texture2D<float4> ").append(name).append("History : register(t").append(12 + i).append(");\n");
            header.append("#define ").append(name).append("Size DirectBufferSize[").append(i).append("]\n");
            if (pass.compute() && pass.outputs().contains(name))
                header.append("RWTexture2D<float4> ").append(name).append("Out : register(u").append(i).append(");\n");
        }
        return header.toString();
    }

    private String[] compileInputs(List<PackRepository.NativePass> passes, Map<String, String> macros,
                                   Map<String, String> defines) {
        return passes.stream().map(pass -> (pass.hdr() ? "// direct:target=rgba16f\n" : "")
                + prepare(pass.source(), pass.file(), macros, defines, false)).toArray(String[]::new);
    }

    private String prepare(String source, String file, Map<String, String> macros, Map<String, String> defines, boolean hooks) {
        StringBuilder aliases = new StringBuilder();
        String array = hooks ? "PackOptions" : "RaydianceData";
        int offset = hooks ? 0 : 8;
        for (int index = 0; index < options.size(); index++) {
            aliases.append("#define ").append(options.get(index).uniform()).append(" (")
                    .append(array).append('[').append(offset + index / 4).append("][").append(index % 4).append("])\n");
        }
        return ShaderMacros.inject(aliases + DefineOptions.apply(source, defines), ShaderSourceLoader.Dialect.HLSL, macros, file);
    }

    @Override public Kind kind() { return Kind.DIRECT; }
    @Override public PackRepository.Pack pack() { return pack; }
    @Override public boolean rejected() { return rejected; }
    @Override public String status() { return status; }

    public void prepareTerrain(float partialTicks) {
        var mc = Minecraft.getInstance();
        try {
            var store = PackOptionStore.live();
            if (store.revision() != optionsRevision) {
                updateOptions(store);
                optionsRevision = store.revision();
            }
            if (hasTerrainPasses || world.length != 0) fillContext(mc, partialTicks);
        } catch (Exception | LinkageError exception) {
            reject(exception.getMessage());
        }
    }

    private void updateOptions(PackOptionStore store) throws ReflectiveOperationException {
        for (int i = 0; i < options.size(); i++) {
            float value = (float) store.value(pack, options.get(i));
            frame[32 + i] = value;
            geometryValues[i] = value;
        }
        var passes = pack.nativePrograms().world();
        var active = new java.util.ArrayList<String>(passes.size());
        for (int i = 0; i < passes.size(); i++) {
            if (store.enabled(pack, passes.get(i).whenAny())) active.add(worldPrograms[i]);
        }
        world = active.toArray(String[]::new);
        worldUniforms = world.length == 1 ? frame : new float[world.length * FRAME_FLOATS];
        if (geometry != null) {
            boolean translucent = geometrySpec.translucent() && store.enabled(pack, geometrySpec.translucentWhenAny());
            direct.geometry(geometry, geometryValues, translucent, translucent && geometrySpec.sceneInputs(), geometrySpec.shadows());
        }
    }

    private void fillContext(Minecraft mc, float partialTicks) {
        var level = mc.level;
        var projection = FrameUniforms.projection;
        var view = FrameUniforms.view;
        projection.get(frame, 64);
        FrameUniforms.inverseProjection.get(frame, 80);
        FrameUniforms.inverseView.get(frame, 96);
        view.get(frame, 112);
        float angle = level.getSunAngle(partialTicks);
        float sunY = (float) Math.cos(angle);
        sun.set(-(float) Math.sin(angle), sunY, 0);
        view.transformDirection(sun);
        frame[128] = sun.x;
        frame[129] = sun.y;
        frame[130] = sun.z;
        boolean hasSkyLight = level.dimensionType().hasSkyLight();
        frame[131] = hasSkyLight ? Math.max(0, sunY) : 0;
        frame[132] = level.getRainLevel(partialTicks);
        frame[133] = level.getThunderLevel(partialTicks);
        frame[134] = (level.getGameTime() % 240000 + partialTicks) / 20f;
        frame[135] = Math.floorMod(level.getDayTime(), 24000);
        var camera = mc.gameRenderer.getMainCamera();
        var pos = camera.getPosition();
        var block = camera.getBlockPosition();
        frame[136] = (float) pos.x;
        frame[137] = (float) pos.y;
        frame[138] = (float) pos.z;
        frame[139] = level.getBrightness(LightLayer.BLOCK, block) / 15f;
        frame[140] = level.getBrightness(LightLayer.SKY, block) / 15f;
        FogType fluid = camera.getFluidInCamera();
        frame[141] = fluid == FogType.WATER ? 1 : fluid == FogType.LAVA ? 2 : 0;
        var sky = level.getSkyColor(pos, partialTicks);
        frame[144] = (float) sky.x;
        frame[145] = (float) sky.y;
        frame[146] = (float) sky.z;
        frame[147] = hasSkyLight ? 1 : 0;
    }

    @Override
    public void render(float partialTicks) {
        if (rejected) return;
        try {
            String failure = direct.terrainFailure();
            if (!failure.isEmpty()) { reject(failure); return; }
            if (world.length == 0) {
                status = "Direct DX12 terrain programs submitted";
            } else {
                if (world.length > 1) {
                    for (int i = 0; i < world.length; i++)
                        System.arraycopy(frame, 0, worldUniforms, i * FRAME_FLOATS, FRAME_FLOATS);
                }
                if (direct.renderWorld(world, worldUniforms)) status = "Direct DX12 shaderpack active";
                else {
                    failure = direct.worldFailure();
                    if (!failure.isEmpty()) { reject(failure); return; }
                    status = "Waiting for Direct DX12 world output";
                }
            }
            DirectShaderBridge.status(status);
        } catch (Exception | LinkageError exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            reject(cause.toString());
        }
    }

    private void reject(String message) {
        status = ShaderErrors.summary(message, 6);
        DirectShaderBridge.status(status);
        rejected = true;
    }

    @Override public void close() { direct.close(); }
}
