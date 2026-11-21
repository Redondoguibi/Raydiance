package com.redondoguibi.raydiance.client.shaders;

import java.util.*;
import java.util.regex.*;

/**
 * Iris-style comment/const directives of a format-3 program.
 *
 * <ul>
 *   <li>{@code /* RENDERTARGETS: 0,1 *&#47;} or {@code /* DRAWBUFFERS:01 *&#47;}; the last one wins, default {@code 0}.</li>
 *   <li>{@code const int colortex1Format = RGBA16F;}, {@code const bool colortex1Clear = false;},
 *       {@code const vec4 colortex1ClearColor = vec4(0,0,0,1);}, {@code const bool colortex1MipmapEnabled = true;}.
 *       As in Iris packs these are usually inside comments, because the format names are not GLSL identifiers.</li>
 * </ul>
 */
public record ProgramDirectives(List<Integer> renderTargets, Map<Integer, String> formats, Map<Integer, Boolean> clear,
                                Map<Integer, float[]> clearColors, Set<Integer> mipmaps) {
    public static final int BUFFERS = 8;
    public static final Map<String, Integer> ALIASES = Map.of("gcolor", 0, "gdepth", 1, "gnormal", 2, "composite", 3,
            "gaux1", 4, "gaux2", 5, "gaux3", 6, "gaux4", 7);
    private static final String BUFFER = "(colortex[0-7]|gcolor|gdepth|gnormal|composite|gaux[1-4])";
    private static final Pattern RENDER_TARGETS = Pattern.compile("/\\*\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+?)\\s*\\*/");
    private static final Pattern DRAW_BUFFERS = Pattern.compile("/\\*\\s*DRAWBUFFERS\\s*:\\s*([0-9]+)\\s*\\*/");
    private static final Pattern FORMAT = Pattern.compile("const\\s+int\\s+" + BUFFER + "Format\\s*=\\s*([A-Za-z0-9_]+)\\s*;");
    private static final Pattern CLEAR = Pattern.compile("const\\s+bool\\s+" + BUFFER + "Clear\\s*=\\s*(true|false)\\s*;");
    private static final Pattern CLEAR_COLOR = Pattern.compile("const\\s+vec4\\s+" + BUFFER + "ClearColor\\s*=\\s*vec4\\s*\\(([^)]*)\\)\\s*;");
    private static final Pattern MIPMAP = Pattern.compile("const\\s+bool\\s+" + BUFFER + "MipmapEnabled\\s*=\\s*(true|false)\\s*;");

    public ProgramDirectives {
        renderTargets = List.copyOf(renderTargets); formats = Map.copyOf(formats); clear = Map.copyOf(clear);
        clearColors = Map.copyOf(clearColors); mipmaps = Set.copyOf(mipmaps);
    }
    public static int buffer(String name) {
        if (name.startsWith("colortex")) return Integer.parseInt(name.substring(8));
        Integer alias = ALIASES.get(name);
        if (alias == null) throw new IllegalArgumentException("Unknown buffer " + name);
        return alias;
    }
    public static ProgramDirectives parse(String source) {
        List<Integer> targets = List.of(0);
        int at = -1;
        for (var m = RENDER_TARGETS.matcher(source); m.find(); ) if (m.start() > at) { at = m.start(); targets = list(m.group(1).split("[,\\s]+")); }
        for (var m = DRAW_BUFFERS.matcher(source); m.find(); ) if (m.start() > at) { at = m.start(); targets = list(m.group(1).split("")); }
        if (targets.isEmpty() || targets.size() > BUFFERS || new HashSet<>(targets).size() != targets.size())
            throw new IllegalArgumentException("Invalid render targets " + targets);
        for (int t : targets) if (t < 0 || t >= BUFFERS) throw new IllegalArgumentException("Render target out of range: " + t);
        Map<Integer, String> formats = new HashMap<>();
        for (var m = FORMAT.matcher(source); m.find(); ) formats.put(buffer(m.group(1)), BufferFormat.of(m.group(2)).name());
        Map<Integer, Boolean> clear = new HashMap<>();
        for (var m = CLEAR.matcher(source); m.find(); ) clear.put(buffer(m.group(1)), Boolean.parseBoolean(m.group(2)));
        Map<Integer, float[]> colors = new HashMap<>();
        for (var m = CLEAR_COLOR.matcher(source); m.find(); ) colors.put(buffer(m.group(1)), color(m.group(2)));
        Set<Integer> mipmaps = new HashSet<>();
        for (var m = MIPMAP.matcher(source); m.find(); ) if (Boolean.parseBoolean(m.group(2))) mipmaps.add(buffer(m.group(1)));
        return new ProgramDirectives(targets, formats, clear, colors, mipmaps);
    }
    private static List<Integer> list(String[] parts) {
        List<Integer> result = new ArrayList<>();
        for (String p : parts) if (!p.isBlank()) result.add(Integer.parseInt(p.strip()));
        return result;
    }
    static float[] color(String text) {
        String[] parts = text.split(",");
        float[] c = new float[4];
        if (parts.length == 1) Arrays.fill(c, Float.parseFloat(parts[0].strip()));
        else if (parts.length == 4) for (int i = 0; i < 4; i++) c[i] = Float.parseFloat(parts[i].strip());
        else throw new IllegalArgumentException("Clear color needs 1 or 4 components");
        for (float v : c) if (!Float.isFinite(v)) throw new IllegalArgumentException("Non-finite clear color");
        return c;
    }

    /** Supported color formats; integer formats are excluded because composite programs sample them as floats. */
    public enum BufferFormat {
        RGBA8(0x8058, 4), RGBA16(0x805B, 8), RGBA16F(0x881A, 8), RGBA32F(0x8814, 16), RGB10_A2(0x8059, 4),
        R11F_G11F_B10F(0x8C3A, 4), R8(0x8229, 1), RG8(0x822B, 2), R16F(0x822D, 2), RG16F(0x822F, 4), R32F(0x822E, 4), RG32F(0x8230, 8);
        public final int gl, bytes;
        BufferFormat(int gl, int bytes) { this.gl = gl; this.bytes = bytes; }
        public static BufferFormat of(String name) {
            try { return valueOf(name.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Unsupported buffer format: " + name); }
        }
    }
}
