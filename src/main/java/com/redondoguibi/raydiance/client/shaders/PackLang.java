package com.redondoguibi.raydiance.client.shaders;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Iris-compatible {@code lang/<locale>.lang} files: {@code option.NAME}, {@code option.NAME.comment} and
 * {@code value.NAME.VALUE}. File names are matched case-insensitively ({@code pt_BR.lang} or {@code pt_br.lang});
 * missing keys fall back to {@code en_us}.
 */
public final class PackLang {
    public static final PackLang EMPTY = new PackLang(Map.of());
    private final Map<String, String> entries;
    private PackLang(Map<String, String> entries) { this.entries = Map.copyOf(entries); }

    public Optional<String> get(String key) { return Optional.ofNullable(entries.get(key)); }
    static PackLang of(Map<String, String> entries) { return new PackLang(entries); }

    public static PackLang load(Path packRoot, String locale) throws IOException {
        Path dir = packRoot.resolve("lang");
        if (!Files.isDirectory(dir) || Files.isSymbolicLink(dir)) return EMPTY;
        Map<String, String> merged = new HashMap<>();
        for (String wanted : List.of("en_us", locale.toLowerCase(Locale.ROOT))) {
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (name.toLowerCase(Locale.ROOT).equals(wanted + ".lang") && Files.isRegularFile(file))
                        merged.putAll(parse(PackRepository.readText(file, 64 * 1024)));
                }
            }
        }
        return new PackLang(merged);
    }
    static Map<String, String> parse(String text) {
        Map<String, String> result = new HashMap<>();
        for (String line : text.split("\\R")) {
            String t = line.strip();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            if (eq <= 0) continue;
            String key = t.substring(0, eq).strip(), value = t.substring(eq + 1).strip();
            // OptiFine/Iris lang files use Minecraft section-sign formatting codes; strip them for plain labels.
            result.put(key, value.replaceAll("§.", ""));
        }
        return result;
    }
}
