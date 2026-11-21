package com.redondoguibi.raydiance.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.redondoguibi.raydiance.client.shaders.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.regex.Pattern;

/** The sibling Solaris pack (Direct/Solaris/solaris) must keep loading under 1.7. Skipped when absent. */
class SolarisCompatibilityTest {
    private static final Path ROOT = Path.of("..", "..", "Direct", "Solaris");
    @Test void solarisLoadsAndKeepsTheNativeUniformContract() throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ROOT.resolve("solaris/raydiance.json")), "Solaris pack not present");
        var pack = PackRepository.read(ROOT, "solaris");
        assertEquals("Solaris", pack.title());
        assertTrue(pack.canRunDirect());
        assertFalse(pack.canRunOpenGl(), "Solaris is DX12-only: Automatic pauses it without Direct");
        assertTrue(pack.uniformOptions().size() <= 32);
        for (String key : DirectPipeline.KEYS) assertTrue(pack.options().containsKey(key), "Missing engine default: " + key);
        assertEquals(6, pack.presets().size());
        assertTrue(pack.shaderOptions().stream().noneMatch(ShaderOption::define), "no accidental define options: "
                + pack.shaderOptions().stream().filter(ShaderOption::define).map(ShaderOption::key).toList());
        String hlsl = ShaderMacros.inject(pack.hlsl(), ShaderSourceLoader.Dialect.HLSL, ShaderMacros.standard("dx12", 1, "minecraft:overworld", 1), "direct.hlsl");
        var options = pack.uniformOptions();
        for (int i = 0; i < options.size(); i++) {
            var o = options.get(i);
            assertTrue(hlsl.contains("#define " + o.uniform() + " val(" + (32 + i) + ")"), o.key() + " must stay float " + (32 + i));
            assertTrue(Pattern.compile("\\b" + o.uniform() + "\\b").matcher(hlsl).results().count() >= 2, "unused " + o.key());
        }
        assertFalse(hlsl.contains("#line 1 \"direct.hlsl\"\n#line"), "line directives stay well formed");
        System.out.println("SOLARIS options=" + options.size() + " hlslBytes=" + hlsl.length() + " files=" + pack.sourceNames().values());
    }
}
