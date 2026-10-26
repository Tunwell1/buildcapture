package com.tunwell.buildcapture;

import com.tunwell.buildcapture.config.BuildCaptureConfig;
import com.tunwell.buildcapture.network.ModNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(BuildCapture.MOD_ID)
public class BuildCapture {
    public static final String MOD_ID = "buildcapture";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public BuildCapture() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BuildCaptureConfig.SPEC);
        ModNetwork.register();
    }
}
