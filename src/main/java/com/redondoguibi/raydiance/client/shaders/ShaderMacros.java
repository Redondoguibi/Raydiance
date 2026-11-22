package com.redondoguibi.raydiance.client.shaders;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Standard preprocessor macros injected into every pack program (compare Iris' StandardMacros).
 *
 * <p>All names are Raydiance-namespaced except {@code MC_VERSION}. Raydiance never defines Iris/OptiFine identity
 * macros, so packs cannot mistake it for a full gbuffer pipeline.</p>
 */
public final class ShaderMacros {
    public static final int VERSION = 10800;
    private static final Pattern VERSION_LINE = Pattern.compile("(?m)^\\s*#\\s*version\\b[^\\n]*\\n");

    public static Map<String, String> standard(String backend, int quality, String dimension, int format) {
        Map<String, String> macros = new LinkedHashMap<>();
        macros.put("RAYDIANCE", "1");
        macros.put("RAYDIANCE_VERSION", Integer.toString(VERSION));
        macros.put("MC_VERSION", "12101");
        macros.put("RAYDIANCE_FORMAT", Integer.toString(format));
        macros.put("RAYDIANCE_QUALITY", Integer.toString(Math.clamp(quality, 0, 2)));
        macros.put("RAYDIANCE_BACKEND_" + backend.toUpperCase(Locale.ROOT), "1");
        macros.put(switch (dimension) {
            case "minecraft:overworld" -> "RAYDIANCE_DIMENSION_OVERWORLD";
            case "minecraft:the_nether" -> "RAYDIANCE_DIMENSION_NETHER";
            case "minecraft:the_end" -> "RAYDIANCE_DIMENSION_END";
            default -> "RAYDIANCE_DIMENSION_OTHER";
        }, "1");
        return macros;
    }

    /**
     * GLSL: inserted right after {@code #version}; the loader's following {@code #line} keeps numbering intact.
     * HLSL: prepended, followed by a {@code #line 1 "entry"} reset.
     */
    public static String inject(String source, ShaderSourceLoader.Dialect dialect, Map<String, String> macros, String entry) {
        StringBuilder block = new StringBuilder();
        macros.forEach((name, value) -> block.append("#define ").append(name).append(' ').append(value).append('\n'));
        if (dialect == ShaderSourceLoader.Dialect.HLSL)
            return block + "#line 1 \"" + entry.replace("\"", "") + "\"\n" + source;
        var version = VERSION_LINE.matcher(source);
        if (!version.find()) return block + source;
        String rest = source.substring(version.end());
        if (!rest.startsWith("#line")) {
            int next = (int) source.substring(0, version.end()).chars().filter(c -> c == '\n').count() + 1;
            block.append("#line ").append(next).append(" 0\n");
        }
        return source.substring(0, version.end()) + block + rest;
    }
    private ShaderMacros() {}
}
