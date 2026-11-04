package com.redondoguibi.raydiance.client.shaders;

import com.redondoguibi.raydiance.Config;
import com.redondoguibi.raydiance.Raydiance;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Raydiance's Direct-only shader format: one local UTF-8 HLSL pixel shader per file.
 * This is NOT an Iris shaderpack loader.
 */
public final class ShaderPackFiles {
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,47}");
    private static final int MAX_SOURCE_BYTES = 256 * 1024;
    public static final String NONE = "none";

    private ShaderPackFiles() {
    }

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Raydiance.MODID).resolve("shaderpacks");
    }

    public static boolean validName(String name) {
        return NAME.matcher(name).matches() && !NONE.equals(name);
    }

    public static List<String> list() throws IOException {
        Path folder = directory();
        Files.createDirectories(folder);
        List<String> result = new ArrayList<>();
        try (var entries = Files.list(folder)) {
            entries.filter(Files::isRegularFile).forEach(path -> {
                String file = path.getFileName().toString();
                if (file.endsWith(".hlsl")) {
                    String name = file.substring(0, file.length() - 5);
                    if (validName(name)) {
                        result.add(name);
                    }
                }
            });
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    public static String read(String name) throws IOException {
        if (!validName(name)) {
            throw new IOException("Shader name must be 1-48 lowercase letters, digits, underscores or hyphens.");
        }
        Path folder = directory();
        Files.createDirectories(folder);
        Path file = folder.resolve(name + ".hlsl");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
            throw new IOException("Shader file does not exist: " + file);
        }
        long size = Files.size(file);
        if (size < 1 || size > MAX_SOURCE_BYTES) {
            throw new IOException("Shader must contain 1 to " + MAX_SOURCE_BYTES + " bytes.");
        }
        String source = Files.readString(file, StandardCharsets.UTF_8);
        if (source.isBlank()) {
            throw new IOException("Shader source is empty.");
        }
        return source;
    }

    public static Path installExample() throws IOException {
        Path folder = directory();
        Files.createDirectories(folder);
        Path target = folder.resolve("softwarm.hlsl");
        try (InputStream stream = ShaderPackFiles.class.getResourceAsStream(
                "/assets/raydiance/examples/softwarm.hlsl")) {
            if (stream == null) {
                throw new IOException("Bundled sample shader resource is missing.");
            }
            Files.copy(stream, target); // Fail rather than overwrite a customized shader.
        }
        return target;
    }

    public static void select(String name) throws IOException {
        if (!NONE.equals(name)) {
            read(name);
        }
        Config.DIRECT_SHADER.set(name);
    }

    public static String selected() {
        return Config.DIRECT_SHADER.get();
    }
}
