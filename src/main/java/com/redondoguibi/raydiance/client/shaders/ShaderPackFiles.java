package com.redondoguibi.raydiance.client.shaders;

import com.redondoguibi.raydiance.Raydiance;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.neoforged.fml.loading.FMLPaths;

/** Locates local shaderpacks and validates identifiers used for their per-pack settings. */
public final class ShaderPackFiles {
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{0,47}");

    private ShaderPackFiles() {}

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(Raydiance.MODID).resolve("shaderpacks");
    }

    public static boolean validName(String name) {
        return NAME.matcher(name).matches() && !"none".equals(name);
    }
}
