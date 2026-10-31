package com.prismora;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Prismora.MOD_ID)
public final class Prismora {
    public static final String MOD_ID = "prismora";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Prismora() {
        LOGGER.info("Prismora initialized; client rendering extensions are isolated from dedicated servers");
    }
}
