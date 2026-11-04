package com.redondoguibi.raydiance.client.shaders;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Local includes with explicit file/depth/output budgets and source-number diagnostics. */
public final class ShaderSourceLoader {
    private static final int MAX_FILES = 64, MAX_DEPTH = 16, MAX_BYTES = 1024 * 1024;
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+\"([^\"]+)\"\\s*(?://.*)?$");
    private final Path root;
    private final Map<Path, String> files = new HashMap<>();
    private final Map<Path, Integer> ids = new LinkedHashMap<>();
    private final Set<Path> visiting = new HashSet<>();
    private int bytes;

    public ShaderSourceLoader(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(this.root) || !Files.isDirectory(this.root)) throw new IOException("Invalid shader root");
    }
    public String load(String file) throws IOException {
        bytes = 0; visiting.clear();
        return expand(resolve(root, file), 0);
    }
    public Map<Integer, String> sourceNames() {
        Map<Integer, String> result = new LinkedHashMap<>();
        ids.forEach((path, id) -> result.put(id, root.relativize(path).toString()));
        return Map.copyOf(result);
    }
    private Path resolve(Path parent, String name) throws IOException {
        if (name.isBlank() || name.contains(":") || name.contains("\\") || name.startsWith("/") ||
                Arrays.asList(name.split("/")).contains("..")) throw new IOException("Invalid shader path: " + name);
        Path path = parent.resolve(name).normalize();
        if (!path.startsWith(root)) throw new IOException("Shader path escapes pack");
        Path cursor = root;
        for (Path segment : root.relativize(path)) {
            cursor = cursor.resolve(segment);
            if (Files.isSymbolicLink(cursor)) throw new IOException("Symlink shader path: " + name);
        }
        if (!path.toRealPath().startsWith(root.toRealPath())) throw new IOException("Shader path resolves outside pack");
        return path;
    }
    private String expand(Path path, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Include nesting exceeds " + MAX_DEPTH);
        if (!visiting.add(path)) throw new IOException("Include cycle at " + root.relativize(path));
        try {
            String source = files.get(path);
            if (source == null) {
                if (files.size() >= MAX_FILES) throw new IOException("Too many include files");
                source = PackRepository.readText(path, 256 * 1024); files.put(path, source);
            }
            int id = ids.computeIfAbsent(path, key -> ids.size() + 1);
            StringBuilder output = new StringBuilder();
            boolean blockComment = false;
            String[] lines = source.split("\\R", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                // Strip comments only for directive detection; retain the actual source verbatim.
                StringBuilder directive = new StringBuilder();
                for (int c = 0; c < line.length(); c++) {
                    if (blockComment) { if (line.startsWith("*/", c)) { blockComment = false; c++; } }
                    else if (line.startsWith("/*", c)) { blockComment = true; c++; }
                    else if (line.startsWith("//", c)) break;
                    else directive.append(line.charAt(c));
                }
                String visible = directive.toString();
                if (depth > 0 && visible.stripLeading().startsWith("#version")) throw new IOException("#version belongs in the entry shader: " + path.getFileName());
                Matcher include = INCLUDE.matcher(visible);
                if (include.matches()) {
                    if(!INCLUDE.matcher(line).matches())throw new IOException("Include directives must be on their own line: "+path.getFileName()+":"+(i+1));
                    Path child = resolve(path.getParent(), include.group(1));
                    int childId = ids.computeIfAbsent(child, key -> ids.size() + 1);
                    append(output, "#line 1 " + childId + "\n");
                    output.append(expand(child, depth + 1));
                    append(output, "#line " + (i + 2) + " " + id + "\n");
                } else {
                    if (visible.matches("\\s*#\\s*include.*")) throw new IOException("Malformed include at " + path.getFileName() + ":" + (i + 1));
                    append(output, line + "\n");
                }
            }
            return output.toString();
        } finally { visiting.remove(path); }
    }
    private void append(StringBuilder output, String text) throws IOException {
        bytes += text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) throw new IOException("Expanded shader exceeds 1 MiB");
        output.append(text);
    }
}
