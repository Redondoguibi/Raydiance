package com.redondoguibi.raydiance.client.shaders;

import java.util.*;
import java.util.regex.*;

/**
 * Iris-style option discovery from preprocessor definitions (compare Iris' OptionAnnotatedSource).
 *
 * <ul>
 *   <li>{@code #define NAME value // [a b c] comment} is a choice option; the default must be listed.</li>
 *   <li>{@code #define NAME // comment} or {@code //#define NAME} is a toggle, but only when the name is tested
 *       with {@code #ifdef}, {@code #ifndef} or {@code defined} and the definition is not an include guard.</li>
 * </ul>
 *
 * <p>Values are applied by rewriting the definition lines, which keeps line numbers and {@code #line} mapping stable.</p>
 */
public final class DefineOptions {
    public static final int MAX_OPTIONS = 128;
    private static final Pattern CHOICE = Pattern.compile("^(\\s*)#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+([^\\s/]+)\\s*//\\s*\\[([^\\]]*)\\](.*)$");
    private static final Pattern TOGGLE = Pattern.compile("^(\\s*)(//\\s*)?#\\s*define\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(//.*)?$");
    private static final Pattern IFNDEF = Pattern.compile("^\\s*#\\s*ifndef\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*(//.*)?$");
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9_.+-]{1,48}");

    public record Found(String name, boolean toggle, List<String> values, String defaultValue, String comment) {}

    /** Discovery over expanded sources; the first definition of a name wins. */
    public static List<Found> discover(Collection<String> sources) {
        Map<String, Found> found = new LinkedHashMap<>();
        Set<String> tested = new HashSet<>(), guards = new HashSet<>();
        Pattern test = Pattern.compile("#\\s*if(?:n?def)?\\b[^\\n]*");
        Pattern names = Pattern.compile("(?:#\\s*ifn?def\\s+|defined\\s*\\(?\\s*)([A-Za-z_][A-Za-z0-9_]*)");
        for (String source : sources) {
            String[] lines = source.split("\\R", -1);
            String previous = "";
            for (String line : lines) {
                var t = test.matcher(line);
                if (t.find()) { var n = names.matcher(t.group()); while (n.find()) tested.add(n.group(1)); }
                var guard = IFNDEF.matcher(previous);
                var toggle = TOGGLE.matcher(line);
                if (guard.matches() && toggle.matches() && toggle.group(2) == null && guard.group(1).equals(toggle.group(3))) guards.add(toggle.group(3));
                if (!line.isBlank() && !line.startsWith("#line")) previous = line;
            }
        }
        for (String source : sources) for (String line : source.split("\\R", -1)) {
            if (found.size() >= MAX_OPTIONS) break;
            var choice = CHOICE.matcher(line);
            if (choice.matches()) {
                String name = choice.group(2);
                List<String> values = Arrays.stream(choice.group(4).trim().split("\\s+")).filter(v -> !v.isEmpty()).distinct().toList();
                if (found.containsKey(name) || values.size() < 2 || values.size() > 64 || !values.stream().allMatch(v -> VALUE.matcher(v).matches())) continue;
                String def = values.stream().filter(v -> ShaderOption.sameValue(v, choice.group(3))).findFirst().orElse(null);
                if (def == null) continue;
                found.put(name, new Found(name, false, values, def, comment(choice.group(5))));
                continue;
            }
            var toggle = TOGGLE.matcher(line);
            if (toggle.matches()) {
                String name = toggle.group(3);
                if (found.containsKey(name) || !tested.contains(name) || guards.contains(name)) continue;
                found.put(name, new Found(name, true, List.of(), toggle.group(2) == null ? "true" : "false", comment(toggle.group(4))));
            }
        }
        return List.copyOf(found.values());
    }
    private static String comment(String text) {
        if (text == null) return "";
        String c = text.strip();
        if (c.startsWith("//")) c = c.substring(2).strip();
        return c.length() > 400 ? c.substring(0, 400) : c;
    }

    public static List<ShaderOption> options(List<Found> found, PackLang lang, Map<String, String> categories) {
        List<ShaderOption> result = new ArrayList<>();
        for (Found f : found) {
            String label = clip(lang.get("option." + f.name()).orElse(pretty(f.name())), 64);
            String description = clip(lang.get("option." + f.name() + ".comment").orElse(f.comment()), 400);
            String category = clip(categories.getOrDefault(f.name(), ShaderOption.DEFINE_CATEGORY), 40);
            if (f.toggle()) {
                result.add(new ShaderOption(ShaderOption.DEFINE_PREFIX + f.name(), label, category, description, "",
                        ShaderOption.Kind.TOGGLE, 0, 1, 1, "true".equals(f.defaultValue()) ? 1 : 0, ShaderOption.Binding.DEFINE, List.of()));
            } else {
                result.add(new ShaderOption(ShaderOption.DEFINE_PREFIX + f.name(), label, category, description, "",
                        ShaderOption.Kind.CHOICE, 0, f.values().size() - 1, 1, f.values().indexOf(f.defaultValue()),
                        ShaderOption.Binding.DEFINE, f.values()));
            }
        }
        return List.copyOf(result);
    }
    private static String clip(String text, int max) { String t = text.isBlank() ? "?" : text; return t.length() > max ? t.substring(0, max) : t; }
    static String pretty(String name) {
        String words = name.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        return words.isEmpty() ? name : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    /**
     * Rewrites definitions for the given overrides; {@code values} maps define names to a choice or "true"/"false".
     * Unknown names and unchanged lines are left untouched.
     */
    public static String apply(String source, Map<String, String> values) {
        if (values.isEmpty()) return source;
        String[] lines = source.split("\n", -1);
        StringBuilder out = new StringBuilder(source.length() + 64);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String cr = line.endsWith("\r") ? "\r" : "";
            String body = cr.isEmpty() ? line : line.substring(0, line.length() - 1);
            var choice = CHOICE.matcher(body);
            var toggle = TOGGLE.matcher(body);
            if (choice.matches() && values.containsKey(choice.group(2))) {
                String v = values.get(choice.group(2));
                if (VALUE.matcher(v).matches())
                    body = choice.group(1) + "#define " + choice.group(2) + " " + v + " // [" + choice.group(4) + "]" + choice.group(5);
            } else if (toggle.matches() && values.containsKey(toggle.group(3))) {
                String suffix = toggle.group(4) == null ? "" : " " + toggle.group(4);
                body = toggle.group(1) + ("true".equals(values.get(toggle.group(3))) ? "" : "//") + "#define " + toggle.group(3) + suffix;
            }
            out.append(body).append(cr);
            if (i < lines.length - 1) out.append('\n');
        }
        return out.toString();
    }
    private DefineOptions() {}
}
