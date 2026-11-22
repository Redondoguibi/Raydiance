package com.redondoguibi.raydiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.redondoguibi.raydiance.client.shaders.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** The sibling Solaris 2.0 pack (Direct Group/solaris) must load as a format-4 Direct pack. Skipped when absent. */
class SolarisCompatibilityTest {
    private static final Path ROOT = Path.of("..");

    @Test void solarisLoadsAsFormatFour() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ROOT.resolve("solaris/raydiance.json")), "Solaris pack not present");
        var pack = PackRepository.read(ROOT, "solaris");
        assertEquals("Solaris", pack.title());
        assertEquals(4, pack.format());
        assertTrue(pack.canRunDirect());
        assertFalse(pack.canRunOpenGl(), "Solaris is DX12-only");
        var programs = pack.nativePrograms();
        assertEquals(List.of("bloom.hlsl", "scene.hlsl", "final.hlsl"), programs.world().stream().map(PackRepository.NativePass::file).toList());
        assertEquals(List.of(true, true, false), programs.world().stream().map(PackRepository.NativePass::hdr).toList());
        assertTrue(programs.terrain().isEmpty());
        assertNotNull(programs.geometry());
        assertTrue(programs.geometry().translucent() && programs.geometry().shadows() && !programs.geometry().sceneInputs());
        PackCompatibility.check(pack, 32, Set.of("QUALITY", "FXAA", "SHARPEN", "CLOUDS", "TONEMAP_CURVE", "AO_SAMPLES", "RAY_SAMPLES",
                "SHADOW_FILTER", "BLOOM_RADIUS", "BLOOM_THRESHOLD", "CHROMATIC", "FILM_GRAIN", "GAMMA", "RAIN_MOOD", "UNDERWATER_TINT",
                "UNDERWATER_FOG", "WATER_GLINT"), 6);
        // The distribution zip (Direct Group/_dist/solaris.zip) loads the same programs and options.
        Path dist = Path.of("..", "_dist");
        if (Files.isRegularFile(dist.resolve("solaris.zip"))) {
            var zipped = PackRepository.read(dist, "solaris");
            assertEquals(pack.shaderOptions(), zipped.shaderOptions());
            assertEquals(pack.nativePrograms().world().size(), zipped.nativePrograms().world().size());
            assertEquals(pack.nativePrograms().geometry().source(), zipped.nativePrograms().geometry().source());
        }
    }
}

/** Shared format-4 checks for the sibling packs. */
final class PackCompatibility {
    static void check(PackRepository.Pack pack, int uniforms, Set<String> defines, int presets) {
        var options = pack.uniformOptions();
        assertEquals(uniforms, options.size());
        var found = new TreeSet<String>();
        pack.shaderOptions().stream().filter(ShaderOption::define).forEach(o -> found.add(o.defineName()));
        assertEquals(new TreeSet<>(defines), found, "define options");
        assertTrue(pack.shaderOptions().stream().filter(ShaderOption::define).noneMatch(o -> o.category().equals(ShaderOption.DEFINE_CATEGORY)),
                "every define has a category");
        // The lang file is read: QUALITY's label and every define's comment come from it, not the fallback.
        assertTrue(pack.shaderOptions().stream().anyMatch(o -> o.define() && o.defineName().equals("QUALITY")
                && !o.label().equals(DefineOptionsLabel.pretty("QUALITY"))), "lang labels loaded");
        assertTrue(pack.shaderOptions().stream().filter(ShaderOption::define).allMatch(o -> o.description().length() > 40),
                "every define has a lang description");
        assertEquals(presets, pack.presets().size());
        var programs = pack.nativePrograms();
        var sources = new ArrayList<String>();
        programs.world().forEach(p -> sources.add(p.source()));
        programs.terrain().forEach(p -> sources.add(p.source()));
        if (programs.geometry() != null) sources.add(programs.geometry().source());
        String all = String.join("\n", sources);
        for (var o : options)
            assertTrue(Pattern.compile("\\b" + o.uniform() + "\\b").matcher(all).find(), "unused " + o.key());
        // Every choice of every define rewrites its line without disturbing the rest of the source.
        for (var o : pack.shaderOptions()) {
            if (!o.define()) continue;
            for (double v = o.min(); v <= o.max(); v += o.step()) {
                for (String source : sources) {
                    String applied = DefineOptions.apply(source, Map.of(o.defineName(), o.sourceValue(v)));
                    assertEquals(source.split("\n", -1).length, applied.split("\n", -1).length, o.key());
                }
            }
        }
        for (var preset : pack.presets()) assertTrue(preset.options().isEmpty(), preset.name() + " uses only shaderValues");
        String hlsl = ShaderMacros.inject(sources.getFirst(), ShaderSourceLoader.Dialect.HLSL,
                ShaderMacros.standard("dx12", 1, "minecraft:overworld", 4), "first.hlsl");
        assertTrue(hlsl.startsWith("#define RAYDIANCE 1"));
        System.out.println(pack.title() + " options=" + options.size() + " defines=" + found.size() + " bytes=" + all.length()
                + " files=" + pack.sourceNames().values());
    }
    private PackCompatibility() {}
}

/** Mirrors DefineOptions' fallback label so the test can detect untranslated defines. */
final class DefineOptionsLabel {
    static String pretty(String name) {
        String words = name.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        return words.isEmpty() ? name : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
    private DefineOptionsLabel() {}
}
