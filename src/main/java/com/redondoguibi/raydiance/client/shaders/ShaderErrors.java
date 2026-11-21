package com.redondoguibi.raydiance.client.shaders;

import java.util.Map;
import java.util.regex.*;

/**
 * Rewrites driver compile logs from numeric {@code #line} source ids to pack file names.
 * Handles NVIDIA {@code 3(12) : error}, AMD/Intel {@code ERROR: 3:12:} and Mesa {@code 3:12(5): error}.
 */
public final class ShaderErrors {
    private static final Pattern NVIDIA = Pattern.compile("(?m)(^|[\\s:])(\\d+)\\((\\d+)\\)(?=\\s*:)");
    private static final Pattern COLON = Pattern.compile("(?m)(^|ERROR:\\s*|WARNING:\\s*|[\\s])(\\d+):(\\d+)(?=[:(])");

    public static String translate(String log, Map<Integer, String> names) {
        if (log == null || names.isEmpty()) return log;
        return replace(replace(log, NVIDIA, names), COLON, names);
    }
    private static String replace(String log, Pattern pattern, Map<Integer, String> names) {
        Matcher m = pattern.matcher(log);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name;
            try { name = names.get(Integer.parseInt(m.group(2))); } catch (NumberFormatException ex) { name = null; }
            String replacement = name == null ? m.group() : m.group(1) + name + ":" + m.group(3);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
    /** First lines only: chat/status lines must stay readable. */
    public static String summary(String message, int maxLines) {
        if (message == null) return "";
        String[] lines = message.strip().split("\\R");
        StringBuilder out = new StringBuilder();
        for (int i = 0, n = 0; i < lines.length && n < maxLines; i++) {
            if (lines[i].isBlank()) continue;
            if (n++ > 0) out.append('\n');
            out.append(lines[i].strip());
        }
        return out.toString();
    }
    private ShaderErrors() {}
}
