package com.redondoguibi.raydiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.redondoguibi.raydiance.client.shaders.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;

/** The sibling Lus 2.0 pack (Direct Group/Lus/lus) must load as a format-4 Direct pack. Skipped when absent. */
class LusCompatibilityTest {
    private static final Path PACKS = Path.of("..", "Lus");

    @Test void performancePresetSkipsInactiveStagesAndIndividualControlsRestoreThem(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PACKS.resolve("lus/raydiance.json")));
        var pack = PackRepository.read(PACKS, "lus");
        var store = new PackOptionStore(temp);
        var world = pack.nativePrograms().world();
        var water = pack.nativePrograms().geometry().translucentWhenAny();
        store.restore(pack, pack.presets().stream().filter(p -> p.name().equals("Desempenho")).findFirst().orElseThrow().shaderValues());
        assertFalse(store.enabled(pack, water));
        assertFalse(store.enabled(pack, world.getFirst().whenAny()));
        assertTrue(store.enabled(pack, world.getLast().whenAny()));
        for (String key : List.of("bloom", "godRays")) {
            var option = pack.uniformOptions().stream().filter(o -> o.key().equals(key)).findFirst().orElseThrow();
            store.set(pack, option, 0.2);
            assertTrue(store.enabled(pack, world.getFirst().whenAny()));
            store.set(pack, option, 0);
        }
        store.reset(pack);
        assertTrue(store.enabled(pack, water));
    }

    @Test void lusLoadsAsFormatFour() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(PACKS.resolve("lus/raydiance.json")), "Lus pack not present");
        var pack = PackRepository.read(PACKS, "lus");
        assertEquals("Lus", pack.title());
        assertEquals(4, pack.format());
        assertTrue(pack.canRunDirect());
        assertFalse(pack.canRunOpenGl(), "Lus 2.0 has no GLSL programs");
        var programs = pack.nativePrograms();
        assertEquals(List.of("bloom.hlsl", "composite.hlsl"), programs.world().stream().map(PackRepository.NativePass::file).toList());
        assertEquals(List.of(true, false), programs.world().stream().map(PackRepository.NativePass::hdr).toList());
        assertNotNull(programs.geometry());
        assertTrue(programs.geometry().translucent() && !programs.geometry().shadows() && !programs.geometry().sceneInputs());
        PackCompatibility.check(pack, 32, Set.of("QUALITY", "FXAA", "SHARPEN", "CLOUDS", "AO_SAMPLES", "CONTACT_STEPS", "CONTACT_REACH",
                "BLOOM_RADIUS", "GAMMA", "WATER_GLINT"), 3);
        // The distribution zip (Direct Group/_dist/lus.zip) loads the same programs and options.
        Path dist = Path.of("..", "_dist");
        if (Files.isRegularFile(dist.resolve("lus.zip"))) {
            var zipped = PackRepository.read(dist, "lus");
            assertEquals(pack.shaderOptions(), zipped.shaderOptions());
            assertEquals(pack.nativePrograms().world().size(), zipped.nativePrograms().world().size());
            assertEquals(pack.nativePrograms().geometry().source(), zipped.nativePrograms().geometry().source());
        }
        // Keys kept from Lus 1.x so saved personal values survive the migration.
        for (String key : List.of("paletteAmount", "vibrance", "nightLift", "shoulder", "lighting", "sky", "sunGlow", "cloudCover",
                "cloudHeight", "cloudSpeed", "hazeStart", "hazeReach", "sunWarmth", "shadowCoolness", "shadowLift", "nightAdaptation",
                "rainDesaturation", "horizonWarmth", "cloudSoftness", "underwaterTint"))
            assertTrue(pack.uniformOptions().stream().anyMatch(o -> o.key().equals(key)), key);
    }
}
