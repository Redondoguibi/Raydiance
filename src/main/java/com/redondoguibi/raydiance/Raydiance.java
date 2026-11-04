package com.redondoguibi.raydiance;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Common mod entry point. Raydiance does not require Direct or another renderer.
 */
@Mod(Raydiance.MODID)
public final class Raydiance {
    public static final String MODID = "raydiance";
    public static final Logger LOGGER = LogUtils.getLogger();
}
