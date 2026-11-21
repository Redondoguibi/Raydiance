package com.redondoguibi.raydiance.client.shaders;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.*;

class DefineOptionsTest {
    private static final String SOURCE = """
            #version 150
            #ifndef LIB_GUARD
            #define LIB_GUARD
            #define QUALITY 2 // [1 2 4] Samples per pixel.
            #define STRENGTH 0.5 // [0.0 0.25 0.5 1.0]
            #define BROKEN 3 // [1 2]
            #define BLOOM // Glow.
            //#define HAZE
            #define UNUSED
            #ifdef BLOOM
            #endif
            #if defined(HAZE) && QUALITY > 1
            #endif
            #endif
            """;

    @Test void discoversChoicesAndReferencedTogglesButNotGuards() {
        var found = DefineOptions.discover(List.of(SOURCE));
        Map<String, DefineOptions.Found> byName = new HashMap<>();
        found.forEach(f -> byName.put(f.name(), f));
        assertEquals(Set.of("QUALITY", "STRENGTH", "BLOOM", "HAZE"), byName.keySet(),
                "include guards, unreferenced toggles and defaults outside the list are not options");
        assertEquals(List.of("1", "2", "4"), byName.get("QUALITY").values());
        assertEquals("2", byName.get("QUALITY").defaultValue());
        assertEquals("Samples per pixel.", byName.get("QUALITY").comment());
        assertEquals("0.5", byName.get("STRENGTH").defaultValue());
        assertEquals("true", byName.get("BLOOM").defaultValue());
        assertEquals("false", byName.get("HAZE").defaultValue());
    }

    @Test void firstDefinitionWinsAcrossPrograms() {
        var found = DefineOptions.discover(List.of("#define A 1 // [1 2]\n#ifdef X\n#endif", "#define A 2 // [2 3]"));
        assertEquals(1, found.size());
        assertEquals(List.of("1", "2"), found.getFirst().values());
    }

    @Test void rewritesOnlyListedDefinitionsAndKeepsLineCount() {
        String rewritten = DefineOptions.apply(SOURCE, Map.of("QUALITY", "4", "BLOOM", "false", "HAZE", "true"));
        assertTrue(rewritten.contains("#define QUALITY 4 // [1 2 4] Samples per pixel."));
        assertTrue(rewritten.contains("//#define BLOOM // Glow."));
        assertTrue(rewritten.lines().anyMatch(l -> l.equals("#define HAZE")));
        assertTrue(rewritten.contains("#define STRENGTH 0.5 // [0.0 0.25 0.5 1.0]"));
        assertEquals(SOURCE.split("\n", -1).length, rewritten.split("\n", -1).length);
        assertEquals(SOURCE, DefineOptions.apply(SOURCE, Map.of()));
        // Rediscovery after rewriting must yield the same options (stable menus after a recompile).
        assertEquals(4, DefineOptions.discover(List.of(rewritten)).size());
    }

    @Test void rejectsInjectedValues() {
        String rewritten = DefineOptions.apply("#define A 1 // [1 2]", Map.of("A", "1\n#error"));
        assertEquals("#define A 1 // [1 2]", rewritten);
    }

    @Test void buildsOptionsWithLangLabelsAndStoresChoiceIndexes() {
        var lang = PackLang.parse("option.QUALITY=§aQuality level\noption.QUALITY.comment=More is slower.\n");
        var options = DefineOptions.options(DefineOptions.discover(List.of(SOURCE)), PackLang.of(lang), Map.of("BLOOM", "Glow"));
        var quality = options.stream().filter(o -> o.defineName().equals("QUALITY")).findFirst().orElseThrow();
        assertEquals("Quality level", quality.label());
        assertEquals("More is slower.", quality.description());
        assertEquals(ShaderOption.Kind.CHOICE, quality.kind());
        assertEquals(1, quality.defaultValue());
        assertEquals("4", quality.sourceValue(2));
        assertEquals(ShaderOption.DEFINE_CATEGORY, quality.category());
        var bloom = options.stream().filter(o -> o.defineName().equals("BLOOM")).findFirst().orElseThrow();
        assertEquals("Glow", bloom.category());
        assertEquals("true", bloom.sourceValue(1));
        assertEquals("Haze", options.stream().filter(o -> o.defineName().equals("HAZE")).findFirst().orElseThrow().label());
    }
}
