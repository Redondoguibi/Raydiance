package com.redondoguibi.raydiance.client.shaders;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Format-3 packs: the bundled Iris-style example, dimension folders, zips and option wiring. */
class ProgramPackTest {
    private static final List<String> EXAMPLE = List.of("raydiance.json", "composite.fsh", "composite1.fsh", "composite2.fsh",
            "final.fsh", "lib/common.glsl", "world-1/final.fsh", "lang/en_us.lang", "lang/pt_br.lang");
    @TempDir Path root;

    private Path install(Path parent, String id) throws IOException {
        Path dir = parent.resolve(id);
        for (String file : EXAMPLE) {
            try (var in = getClass().getResourceAsStream("/assets/raydiance/shaders/examples/iris_style_demo/" + file)) {
                assertNotNull(in, "bundled example file " + file);
                Path target = dir.resolve(file); Files.createDirectories(target.getParent()); Files.copy(in, target);
            }
        }
        return dir;
    }
    private static ShaderOption option(PackRepository.Pack pack, String define) {
        return pack.shaderOptions().stream().filter(o -> o.defineName().equals(define)).findFirst().orElseThrow();
    }

    @Test void loadsBundledExampleWithProgramsBuffersAndDefineOptions() throws Exception {
        install(root, "demo");
        var pack = PackRepository.read(root, "demo", new PackRepository.LoadContext("minecraft:overworld", "pt_br"));
        assertEquals(3, pack.format());
        assertEquals(List.of("composite", "composite1", "composite2", "final"), pack.programs().stream().map(PackRepository.Program::name).toList());
        assertEquals("final.fsh", pack.programs().getLast().file());
        assertFalse(pack.builtinEffects(), "format 3 owns the image unless it opts in");
        assertEquals(Set.of(0, 1), pack.buffers().keySet());
        var bloom = pack.buffers().get(1);
        assertEquals(ProgramDirectives.BufferFormat.RGBA16F, bloom.format());
        assertEquals(.5, bloom.scale());
        assertTrue(bloom.clear());
        assertFalse(pack.buffers().get(0).clear(), "colortex0 holds the frame input");
        assertEquals("BLOOM", pack.programs().getFirst().enabledBy());
        assertEquals(List.of(1), pack.programs().getFirst().directives().renderTargets());

        assertEquals(Set.of("BLOOM", "BLOOM_STRENGTH", "BLOOM_THRESHOLD", "TONEMAP", "VIGNETTE", "HAZE"),
                new HashSet<>(pack.shaderOptions().stream().filter(ShaderOption::define).map(ShaderOption::defineName).toList()));
        assertFalse(pack.shaderOptions().stream().anyMatch(o -> o.defineName().equals("COMMON_GLSL")), "include guard");
        assertEquals("Intensidade do bloom", option(pack, "BLOOM_STRENGTH").label(), "pt_br.lang overrides en_us.lang");
        assertEquals("Bloom", option(pack, "BLOOM_STRENGTH").category());
        assertEquals(0, option(pack, "HAZE").defaultValue());
        var vivid = pack.presets().stream().filter(p -> p.name().equals("Vivid")).findFirst().orElseThrow();
        assertEquals(2.0, vivid.shaderValues().get("define:TONEMAP"), "preset values resolve to choice indexes");
        assertEquals(1.0, vivid.shaderValues().get("define:HAZE"));

        assertTrue(pack.canRunOpenGl());
        assertFalse(pack.canRunDirect(), "no direct.hlsl and GLSL programs: DX12 cannot run it");
        assertTrue(pack.sourceNames().containsValue("lib/common.glsl"));
        assertTrue(PackRepository.bufferBytes(pack.buffers(), 100, 100) > 0);
    }

    @Test void definesAreStoredAndRenderedAsSourceValues() throws Exception {
        install(root, "demo");
        var pack = PackRepository.read(root, "demo");
        var store = new PackOptionStore(root.resolve("options"));
        store.prepare(pack);
        assertEquals("0.35", store.defineValues(pack).get("BLOOM_STRENGTH"));
        store.set(pack, option(pack, "BLOOM_STRENGTH"), 4);
        store.set(pack, option(pack, "HAZE"), 1);
        var values = store.defineValues(pack);
        assertEquals("0.5", values.get("BLOOM_STRENGTH"));
        assertEquals("true", values.get("HAZE"));
        String rewritten = DefineOptions.apply(pack.programs().getLast().source(), values);
        assertTrue(rewritten.contains("#define BLOOM_STRENGTH 0.5 //"));
        assertTrue(rewritten.lines().anyMatch(l -> l.startsWith("#define HAZE")));
        store.save(pack);
        var restarted = new PackOptionStore(root.resolve("options"));
        restarted.prepare(pack);
        assertEquals(values, restarted.defineValues(pack));
    }

    @Test void dimensionFoldersOverrideFilesAndFallBackToTheRoot() throws Exception {
        install(root, "demo");
        var nether = PackRepository.read(root, "demo", new PackRepository.LoadContext("minecraft:the_nether", "en_us"));
        assertEquals("world-1/final.fsh", nether.programs().getLast().file());
        assertEquals("composite.fsh", nether.programs().getFirst().file(), "missing overlay files come from the root");
        assertTrue(nether.shaderOptions().stream().noneMatch(o -> o.defineName().equals("HAZE")), "the Nether final never tests HAZE");
        assertFalse(nether.presets().getLast().shaderValues().containsKey("define:HAZE"), "presets skip defines absent in this dimension");
        var end = PackRepository.read(root, "demo", new PackRepository.LoadContext("minecraft:the_end", "en_us"));
        assertEquals("final.fsh", end.programs().getLast().file());
        Path manifest = root.resolve("demo/raydiance.json");
        Files.writeString(manifest, Files.readString(manifest).replace("\"format\": 3,", "\"format\": 3, \"dimensions\": {\"mymod:moon\": \"world-1\"},"));
        var moon = PackRepository.read(root, "demo", new PackRepository.LoadContext("mymod:moon", "en_us"));
        assertEquals("world-1/final.fsh", moon.programs().getLast().file());
        Files.writeString(manifest, Files.readString(manifest).replace("\"world-1\"}", "\"../x\"}"));
        assertThrows(IOException.class, () -> PackRepository.read(root, "demo"));
    }

    @Test void readsZipPacksWithAOneFolderLayout() throws Exception {
        Path source = install(Files.createDirectory(root.resolve("src")), "demo");
        try (var zip = new ZipOutputStream(Files.newOutputStream(root.resolve("zipped.zip")))) {
            zip.putNextEntry(new ZipEntry("Leia-me.txt")); zip.write("extra".getBytes()); zip.closeEntry();
            for (String file : EXAMPLE) { zip.putNextEntry(new ZipEntry("demo/" + file)); zip.write(Files.readAllBytes(source.resolve(file))); zip.closeEntry(); }
        }
        var pack = PackRepository.read(root, "zipped");
        assertEquals("zipped", pack.id());
        assertEquals(4, pack.programs().size());
        try (var zip = new ZipOutputStream(Files.newOutputStream(root.resolve("empty.zip")))) {
            zip.putNextEntry(new ZipEntry("readme.txt")); zip.write(1); zip.closeEntry();
        }
        assertThrows(IOException.class, () -> PackRepository.read(root, "empty"));
    }

    @Test void validatesProgramSettingsAndBuffers() throws Exception {
        Path dir = Files.createDirectory(root.resolve("bad"));
        Files.writeString(dir.resolve("final.fsh"), "#version 150\n/* RENDERTARGETS: 0,2 */\nvoid main(){}");
        Files.writeString(dir.resolve("raydiance.json"), "{\"format\":3,\"buffers\":{\"colortex2\":{\"scale\":0.5}}}");
        assertThrows(IOException.class, () -> PackRepository.read(root, "bad"), "MRT targets must share a scale");
        Files.writeString(dir.resolve("raydiance.json"), "{\"format\":3,\"buffers\":{\"colortex0\":{\"scale\":0.5}}}");
        assertThrows(IOException.class, () -> PackRepository.read(root, "bad"), "colortex0 is full resolution");
        Files.writeString(dir.resolve("raydiance.json"), "{\"format\":3,\"programs\":{\"final\":{\"enabled\":\"MISSING\"}}}");
        assertThrows(IOException.class, () -> PackRepository.read(root, "bad"), "enabled must name a toggle define");
        Files.writeString(dir.resolve("raydiance.json"), "{\"format\":3,\"programs\":{\"deferred\":{}}}");
        assertThrows(IOException.class, () -> PackRepository.read(root, "bad"), "gbuffer/deferred stages are not supported");
        Files.delete(dir.resolve("final.fsh"));
        Files.writeString(dir.resolve("raydiance.json"), "{\"format\":3}");
        assertThrows(IOException.class, () -> PackRepository.read(root, "bad"), "format 3 needs a program");
    }
}
