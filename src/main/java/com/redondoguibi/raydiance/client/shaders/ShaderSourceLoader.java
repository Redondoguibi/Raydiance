package com.redondoguibi.raydiance.client.shaders;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Local includes with explicit file/depth/output budgets and source-number diagnostics.
 *
 * <p>Paths resolve relative to the including file; a leading {@code /} resolves from the pack root, as in Iris.
 * GLSL output uses numeric {@code #line N id} directives (see {@link #sourceNames()}); HLSL output uses
 * {@code #line N "file"} because FXC rejects numeric source ids.</p>
 */
public final class ShaderSourceLoader {
    public enum Dialect { GLSL, HLSL }
    private static final int MAX_FILES = 64, MAX_DEPTH = 16, MAX_BYTES = 1024 * 1024;
    private static final Pattern INCLUDE = Pattern.compile("^\\s*#\\s*include\\s+\"([^\"]+)\"\\s*(?://.*)?$");
    private static final Pattern VERSION = Pattern.compile("^\\s*#\\s*version\\b.*");
    private final Path root;
    private final Dialect dialect;
    private final Map<Path, String> files = new HashMap<>();
    private final Map<Path, Integer> ids = new LinkedHashMap<>();
    private final Set<Path> visiting = new HashSet<>();
    private int bytes;

    public ShaderSourceLoader(Path root) throws IOException { this(root, Dialect.GLSL); }
    public ShaderSourceLoader(Path root, Dialect dialect) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.dialect = dialect;
        if (Files.isSymbolicLink(this.root) || !Files.isDirectory(this.root)) throw new IOException("Invalid shader root");
    }
    public Path root() { return root; }
    public boolean exists(String file) {
        try { return Files.isRegularFile(resolve(root, file)); } catch (IOException ex) { return false; }
    }
    public String load(String file) throws IOException {
        bytes = 0; visiting.clear();
        return expand(resolve(root, file), 0);
    }
    /** Numeric GLSL source ids to pack-relative file names, for compiler log translation. */
    public Map<Integer, String> sourceNames() {
        Map<Integer, String> result = new LinkedHashMap<>();
        ids.forEach((path, id) -> result.put(id, name(path)));
        return Map.copyOf(result);
    }
    private String name(Path path) { return root.relativize(path).toString().replace('\\', '/'); }
    private Path resolve(Path parent, String name) throws IOException {
        boolean fromRoot = name.startsWith("/");
        String relative = fromRoot ? name.substring(1) : name;
        if (relative.isBlank() || relative.startsWith("/") || relative.contains(":") || relative.contains("\\") ||
                Arrays.asList(relative.split("/")).contains("..")) throw new IOException("Invalid shader path: " + name);
        Path path = (fromRoot ? root : parent).resolve(relative).normalize();
        if (!path.startsWith(root)) throw new IOException("Shader path escapes pack");
        Path cursor = root;
        for (Path segment : root.relativize(path)) {
            cursor = cursor.resolve(segment.toString());
            if (Files.isSymbolicLink(cursor)) throw new IOException("Symlink shader path: " + name);
        }
        if (!Files.exists(path)) throw new IOException("Missing shader file: " + name);
        if (!path.toRealPath().startsWith(root.toRealPath())) throw new IOException("Shader path resolves outside pack");
        return path;
    }
    private String line(int number, int id, Path path) {
        return dialect == Dialect.GLSL ? "#line " + number + " " + id + "\n" : "#line " + number + " \"" + name(path) + "\"\n";
    }
    private String expand(Path path, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Include nesting exceeds " + MAX_DEPTH);
        if (!visiting.add(path)) throw new IOException("Include cycle at " + name(path));
        try {
            String source = files.get(path);
            if (source == null) {
                if (files.size() >= MAX_FILES) throw new IOException("Too many include files");
                source = PackRepository.readText(path, 256 * 1024); files.put(path, source);
            }
            int id = ids.computeIfAbsent(path, key -> ids.size() + 1);
            StringBuilder output = new StringBuilder();
            boolean blockComment = false, versioned = false;
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
                boolean version = VERSION.matcher(visible).matches();
                if (depth > 0 && version) throw new IOException("#version belongs in the entry shader: " + path.getFileName());
                Matcher include = INCLUDE.matcher(visible);
                if (include.matches()) {
                    if(!INCLUDE.matcher(line).matches())throw new IOException("Include directives must be on their own line: "+path.getFileName()+":"+(i+1));
                    Path child = resolve(path.getParent(), include.group(1));
                    int childId = ids.computeIfAbsent(child, key -> ids.size() + 1);
                    append(output, line(1, childId, child));
                    output.append(expand(child, depth + 1));
                    append(output, line(i + 2, id, path));
                } else {
                    if (visible.matches("\\s*#\\s*include.*")) throw new IOException("Malformed include at " + path.getFileName() + ":" + (i + 1));
                    append(output, line + "\n");
                    // Lines before the first include would otherwise report source 0 instead of the entry file.
                    if (version && !versioned && dialect == Dialect.GLSL) { versioned = true; append(output, line(i + 2, id, path)); }
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
