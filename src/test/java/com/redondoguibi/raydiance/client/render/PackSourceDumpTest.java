package com.redondoguibi.raydiance.client.render;

import com.redondoguibi.raydiance.client.shaders.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;

/**
 * Benchmark support, skipped unless RAYDIANCE_DUMP_DIR is set: writes a pack's native programs exactly as
 * {@link DirectPipeline} prepares them (option aliases, define values, standard macros) for one preset, plus
 * the option block. Direct's isolated benchmarks read these files; nothing here runs Minecraft.
 */
class PackSourceDumpTest {
    @Test void dumpPreparedSources(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        String target = System.getenv("RAYDIANCE_DUMP_DIR");
        Assumptions.assumeTrue(target != null && !target.isBlank(), "RAYDIANCE_DUMP_DIR not set");
        String id = Objects.requireNonNullElse(System.getenv("RAYDIANCE_DUMP_PACK"), "lus");
        String preset = Objects.requireNonNullElse(System.getenv("RAYDIANCE_DUMP_PRESET"), "Desempenho");
        Path root = Path.of(Objects.requireNonNullElse(System.getenv("RAYDIANCE_DUMP_ROOT"), "../Lus"));
        var pack = PackRepository.read(root, id);
        // RAYDIANCE_DUMP_OPTIONS: a pack-options directory (e.g. a copy of an instance's config/raydiance/pack-options)
        // whose saved values are used as they are, instead of a preset.
        String saved = System.getenv("RAYDIANCE_DUMP_OPTIONS");
        var store = new PackOptionStore(saved != null && !saved.isBlank() ? Path.of(saved) : temp);
        if (saved == null || saved.isBlank())
            pack.presets().stream().filter(p -> p.name().equals(preset)).findFirst()
                    .ifPresent(p -> store.restore(pack, p.shaderValues()));
        else store.prepare(pack);
        var defines = store.defineValues(pack);
        var macros = ShaderMacros.standard("dx12", 1, "minecraft:overworld", pack.format());
        var options = pack.uniformOptions();
        Path out = Files.createDirectories(Path.of(target));
        var programs = pack.nativePrograms();
        int index = 0;
        for (var pass : programs.world()) {
            if (!store.enabled(pack, pass.whenAny())) continue;
            String source = (pass.hdr() ? "// direct:target=rgba16f\n" : "") + prepare(pass.source(), pass.file(), options, macros, defines, false);
            Files.writeString(out.resolve("world-" + index++ + ".hlsl"), source);
        }
        if (programs.geometry() != null)
            Files.writeString(out.resolve("geometry.hlsl"), prepare(programs.geometry().source(), programs.geometry().file(), options, macros, defines, true));
        StringBuilder values = new StringBuilder();
        for (var option : options) values.append(store.value(pack, option)).append('\n');
        Files.writeString(out.resolve("options.txt"), values);
    }

    private static String prepare(String source, String file, List<ShaderOption> options, Map<String, String> macros,
                                  Map<String, String> defines, boolean hooks) {
        StringBuilder aliases = new StringBuilder();
        String array = hooks ? "PackOptions" : "RaydianceData";
        int offset = hooks ? 0 : 8;
        for (int i = 0; i < options.size(); i++)
            aliases.append("#define ").append(options.get(i).uniform()).append(" (").append(array).append('[')
                    .append(offset + i / 4).append("][").append(i % 4).append("])\n");
        return ShaderMacros.inject(aliases + DefineOptions.apply(source, defines), ShaderSourceLoader.Dialect.HLSL, macros, file);
    }
}
