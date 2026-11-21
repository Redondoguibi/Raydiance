package com.redondoguibi.raydiance.client.shaders;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;

/** Macros, #line mapping, root-relative includes, HLSL dialect, compiler log translation and directives. */
class ShaderPreprocessTest {
    @TempDir Path root;
    private void file(String name, String source) throws Exception {
        Path p = root.resolve(name); Files.createDirectories(p.getParent()); Files.writeString(p, source);
    }

    @Test void entryLinesMapToTheEntryFileAfterVersion() throws Exception {
        file("main.fsh", "#version 150\nvoid main(){}\n#include \"lib/a.glsl\"\nfloat after;");
        file("lib/a.glsl", "float a;");
        var loader = new ShaderSourceLoader(root);
        String s = loader.load("main.fsh");
        var lines = s.split("\n");
        assertEquals("#version 150", lines[0]);
        assertEquals("#line 2 1", lines[1], "lines after #version belong to the entry, not source 0");
        assertEquals(Map.of(1, "main.fsh", 2, "lib/a.glsl"), loader.sourceNames());
    }

    @Test void rootRelativeIncludesResolveFromThePackRoot() throws Exception {
        file("lib/common.glsl", "float common;");
        file("world-1/final.fsh", "#version 150\n#include \"/lib/common.glsl\"\nvoid main(){}");
        assertTrue(new ShaderSourceLoader(root).load("world-1/final.fsh").contains("float common;"));
        for (String bad : new String[]{"//lib/common.glsl", "/../x.glsl", "/C:/x"}) {
            file("bad.fsh", "#version 150\n#include \"" + bad + "\"");
            assertThrows(java.io.IOException.class, () -> new ShaderSourceLoader(root).load("bad.fsh"), bad);
        }
    }

    @Test void hlslUsesQuotedFileNamesInLineDirectives() throws Exception {
        file("direct.hlsl", "#include \"lib/x.hlsl\"\nfloat4 main():SV_Target{return 1;}");
        file("lib/x.hlsl", "static float x=1;");
        String s = new ShaderSourceLoader(root, ShaderSourceLoader.Dialect.HLSL).load("direct.hlsl");
        assertTrue(s.contains("#line 1 \"lib/x.hlsl\""));
        assertTrue(s.contains("#line 2 \"direct.hlsl\""));
        assertFalse(s.matches("(?s).*#line \\d+ \\d+.*"), "FXC rejects numeric source ids");
    }

    @Test void macrosFollowVersionAndKeepNumbering() throws Exception {
        file("main.fsh", "#version 150\nvoid main(){}");
        String expanded = new ShaderSourceLoader(root).load("main.fsh");
        var macros = ShaderMacros.standard("opengl", 2, "minecraft:the_nether", 3);
        String s = ShaderMacros.inject(expanded, ShaderSourceLoader.Dialect.GLSL, macros, "main.fsh");
        var lines = s.split("\n");
        assertEquals("#version 150", lines[0]);
        assertTrue(s.contains("#define RAYDIANCE_BACKEND_OPENGL 1"));
        assertTrue(s.contains("#define RAYDIANCE_DIMENSION_NETHER 1"));
        assertTrue(s.contains("#define RAYDIANCE_QUALITY 2"));
        assertTrue(s.contains("#define MC_VERSION 12101"));
        assertFalse(s.contains("IRIS"), "Raydiance never claims to be Iris");
        assertEquals("#line 2 1", lines[1 + macros.size()], "the loader's #line directive follows the injected block");
        String bare = ShaderMacros.inject("#version 150\nvoid main(){}", ShaderSourceLoader.Dialect.GLSL, Map.of("A", "1"), "x");
        assertEquals("#version 150\n#define A 1\n#line 2 0\nvoid main(){}", bare);
        assertTrue(ShaderMacros.inject("float4 main(){}", ShaderSourceLoader.Dialect.HLSL, Map.of("A", "1"), "direct.hlsl")
                .startsWith("#define A 1\n#line 1 \"direct.hlsl\"\n"));
    }

    @Test void translatesDriverLogsToFileNames() {
        var names = Map.of(1, "final.fsh", 2, "lib/common.glsl");
        assertEquals("lib/common.glsl:12 : error C1008: undefined variable", ShaderErrors.translate("2(12) : error C1008: undefined variable", names));
        assertEquals("ERROR: final.fsh:7: 'x' : undeclared", ShaderErrors.translate("ERROR: 1:7: 'x' : undeclared", names));
        assertEquals("lib/common.glsl:3(14): error: syntax", ShaderErrors.translate("2:3(14): error: syntax", names));
        assertEquals("composite.fsh: final.fsh:4 : error", ShaderErrors.translate("composite.fsh: 1(4) : error", names));
        assertEquals("9(4) : error", ShaderErrors.translate("9(4) : error", names), "unknown ids stay untouched");
        assertEquals("a\nb", ShaderErrors.summary("\n a \n\n b \n c", 2));
    }

    @Test void parsesIrisStyleDirectives() {
        var d = ProgramDirectives.parse("""
                /* DRAWBUFFERS:01 */
                /* RENDERTARGETS: 2, 4 */
                /*
                const int colortex2Format = RGBA16F;
                const int gaux1Format = R11F_G11F_B10F;
                const bool colortex4Clear = false;
                const vec4 colortex4ClearColor = vec4(0.0, 0.5, 1.0, 1.0);
                const bool colortex2MipmapEnabled = true;
                */
                """);
        assertEquals(List.of(2, 4), d.renderTargets(), "the last directive wins");
        assertEquals("RGBA16F", d.formats().get(2));
        assertEquals("R11F_G11F_B10F", d.formats().get(4), "gaux1 is colortex4");
        assertFalse(d.clear().get(4));
        assertArrayEquals(new float[]{0, .5f, 1, 1}, d.clearColors().get(4));
        assertEquals(Set.of(2), d.mipmaps());
        assertEquals(List.of(0), ProgramDirectives.parse("void main(){}").renderTargets());
        assertEquals(List.of(0, 1), ProgramDirectives.parse("/* DRAWBUFFERS:01 */").renderTargets());
        assertThrows(IllegalArgumentException.class, () -> ProgramDirectives.parse("/* RENDERTARGETS: 1,1 */"));
        assertThrows(IllegalArgumentException.class, () -> ProgramDirectives.parse("/* RENDERTARGETS: 9 */"));
        assertThrows(IllegalArgumentException.class, () -> ProgramDirectives.parse("/* const int colortex1Format = RGBA32UI; */"));
    }
}
