package com.redondoguibi.raydiance.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.redondoguibi.raydiance.client.shaders.PackRepository;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Format-4 world buffers, multiple outputs, compute passes and vanilla replacement (Direct API 1.7). */
class WorldGraphManifestTest {
    @TempDir Path packs;

    static final String GRAPHICS = "float4 main(float4 p : SV_Position) : SV_Target0 { return 1; }\n";
    static final String MRT = "struct O { float4 c : SV_Target0; float4 b : SV_Target1; };\nO main(float4 p : SV_Position) { O o; o.c = 1; o.b = bloom.Load(int3(0, 0, 0)); return o; }\n";
    static final String COMPUTE = "[numthreads(8, 8, 1)] void main(uint3 id : SV_DispatchThreadID) { lumOut[id.xy] = taaHistory.Load(int3(id.xy, 0)) * lumSize.z; }\n";

    Path pack(String manifest, Map<String, String> files) throws IOException {
        Path dir = Files.createDirectories(packs.resolve("graph"));
        Files.writeString(dir.resolve("raydiance.json"), manifest);
        for (var file : files.entrySet()) Files.writeString(dir.resolve(file.getKey()), file.getValue());
        return dir;
    }

    static final String VALID = """
            {"format": 4, "name": "Graph",
             "buffers": {"bloom": {"format": "rgba16f", "scale": 0.5, "clear": true},
                         "taa": {"format": "rgba16f", "history": true},
                         "lum": {"format": "r32f", "scale": 0.25}},
             "terrainDepth": true,
             "replaceVanilla": ["sky", "clouds"],
             "worldPasses": [
               {"file": "bloom.hlsl", "outputs": ["bloom"]},
               {"file": "lum.hlsl", "compute": true, "outputs": ["lum"]},
               {"file": "taa.hlsl", "outputs": ["color", "taa"]},
               "final.hlsl"]}
            """;

    PackRepository.Pack valid() throws IOException {
        pack(VALID, Map.of("bloom.hlsl", GRAPHICS, "lum.hlsl", COMPUTE, "taa.hlsl", MRT, "final.hlsl", GRAPHICS));
        return PackRepository.read(packs, "graph");
    }

    @Test void buffersOutputsAndComputeAreParsedInOrder() throws IOException {
        var programs = valid().nativePrograms();
        assertEquals(List.of("bloom", "taa", "lum"), programs.buffers().stream().map(PackRepository.NativeBuffer::name).toList());
        assertEquals(List.of(0, 1, 2), programs.buffers().stream().map(PackRepository.NativeBuffer::index).toList());
        assertEquals(0.5, programs.buffers().getFirst().scale());
        assertTrue(programs.buffers().get(1).history());
        assertTrue(programs.terrainDepth());
        assertEquals(List.of("sky", "clouds"), programs.replaceVanilla());
        assertTrue(programs.world().get(1).compute());
        assertEquals(List.of("color", "taa"), programs.world().get(2).outputs());
        assertTrue(programs.usesGraph());
    }

    @Test void headersCarryDirectivesAndDeclarations() throws IOException {
        var programs = valid().nativePrograms();
        String bloom = DirectPipeline.worldHeader(programs.world().get(0), programs);
        assertTrue(bloom.contains("// direct:buffer 0 rgba16f scale=0.5 clear\n"), bloom);
        assertTrue(bloom.contains("// direct:buffer 1 rgba16f history\n"), bloom);
        assertTrue(bloom.contains("// direct:buffer 2 r32f scale=0.25\n"), bloom);
        assertTrue(bloom.contains("// direct:outputs 0\n"), bloom);
        assertTrue(bloom.contains("// direct:terrain-depth\n"), bloom);
        assertTrue(bloom.contains("Texture2D<float4> taaHistory : register(t13);"), bloom);
        assertTrue(bloom.contains("#define lumSize DirectBufferSize[2]"), bloom);
        assertFalse(bloom.contains("RWTexture2D"), "graphics passes declare no UAVs");
        String lum = DirectPipeline.worldHeader(programs.world().get(1), programs);
        assertTrue(lum.contains("// direct:compute\n"), lum);
        assertTrue(lum.contains("RWTexture2D<float4> lumOut : register(u2);"), lum);
        assertTrue(DirectPipeline.worldHeader(programs.world().get(2), programs).contains("// direct:outputs color,1\n"));
        // A pass without directives and a pack without graph features keep the 1.5 contract exactly.
        String plain = DirectPipeline.worldHeader(programs.world().get(3), programs);
        assertFalse(plain.contains("// direct:outputs"));
        var legacy = new PackRepository.NativePrograms(List.of(), List.of(new PackRepository.NativePass("a.hlsl", "", true, List.of())), null);
        assertEquals("// direct:target=rgba16f\n", DirectPipeline.worldHeader(legacy.world().getFirst(), legacy));
    }

    /** Compiles the generated sources with the Windows SDK fxc when it is installed (Direct's flags and profiles). */
    @Test void generatedSourcesCompileWithFxc() throws Exception {
        var fxc = fxc();
        Assumptions.assumeTrue(fxc.isPresent(), "fxc not installed");
        var programs = valid().nativePrograms();
        String[] bodies = {GRAPHICS, COMPUTE, MRT, GRAPHICS};
        for (int i = 0; i < bodies.length; i++) {
            var pass = programs.world().get(i);
            Path source = packs.resolve("compiled-" + i + ".hlsl");
            Files.writeString(source, DirectPipeline.worldHeader(pass, programs) + bodies[i]);
            var process = new ProcessBuilder(fxc.get().toString(), "/nologo", "/Ges", "/O3", "/T", pass.compute() ? "cs_5_1" : "ps_5_1",
                    "/E", "main", "/D", "DIRECT_WORLD_GRAPH=1", "/Fo", packs.resolve("out-" + i + ".cso").toString(), source.toString())
                    .redirectErrorStream(true).start();
            String log = new String(process.getInputStream().readAllBytes());
            assertEquals(0, process.waitFor(), pass.file() + ":\n" + log);
        }
    }

    static Optional<Path> fxc() throws IOException {
        Path kits = Path.of("C:/Program Files (x86)/Windows Kits/10/bin");
        if (!Files.isDirectory(kits)) return Optional.empty();
        try (var versions = Files.list(kits)) {
            return versions.map(v -> v.resolve("x64/fxc.exe")).filter(Files::isRegularFile).max(Comparator.naturalOrder());
        }
    }

    @Test void invalidGraphsFailAtLoad() throws IOException {
        record Case(String manifest, String message) {}
        var cases = List.of(
                new Case("{\"format\":4,\"worldPasses\":[{\"file\":\"a.hlsl\",\"outputs\":[\"nope\"]},\"b.hlsl\"]}", "undeclared"),
                new Case("{\"format\":4,\"buffers\":{\"x\":{}},\"worldPasses\":[{\"file\":\"a.hlsl\",\"compute\":true}]}", "compute"),
                new Case("{\"format\":4,\"buffers\":{\"x\":{}},\"worldPasses\":[{\"file\":\"a.hlsl\",\"outputs\":[\"x\"]}]}", "final world pass"),
                new Case("{\"format\":4,\"buffers\":{\"x\":{\"scale\":0.5}},\"worldPasses\":[{\"file\":\"a.hlsl\",\"outputs\":[\"color\",\"x\"]}]}", "same size"),
                new Case("{\"format\":4,\"buffers\":{\"x\":{\"format\":\"bgra4\"}},\"worldPasses\":[\"a.hlsl\"]}", "format"),
                new Case("{\"format\":4,\"buffers\":{\"color\":{}},\"worldPasses\":[\"a.hlsl\"]}", "identifiers"),
                new Case("{\"format\":4,\"replaceVanilla\":[\"weather\"],\"worldPasses\":[\"a.hlsl\"]}", "replaceVanilla"),
                new Case("{\"format\":4,\"terrainPasses\":[{\"file\":\"a.hlsl\",\"outputs\":[\"color\"]}]}", "world pass"));
        for (var test : cases) {
            Path dir = pack(test.manifest(), Map.of("a.hlsl", GRAPHICS, "b.hlsl", GRAPHICS));
            var error = assertThrows(IOException.class, () -> PackRepository.read(packs, "graph"), test.manifest());
            assertTrue(String.valueOf(error.getMessage()).contains(test.message()) || String.valueOf(error.getCause()).contains(test.message()),
                    test.manifest() + " -> " + error.getMessage());
            Files.delete(dir.resolve("raydiance.json"));
        }
    }

    @Test void sixteenWorldPassesAreAccepted() throws IOException {
        var files = new HashMap<String, String>();
        var names = new ArrayList<String>();
        for (int i = 0; i < 16; i++) { files.put("p" + i + ".hlsl", GRAPHICS); names.add("\"p" + i + ".hlsl\""); }
        pack("{\"format\":4,\"worldPasses\":[" + String.join(",", names) + "]}", files);
        assertEquals(16, PackRepository.read(packs, "graph").nativePrograms().world().size());
    }
}
