package com.tunwell.buildcapture.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BuildCaptureConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue CAPTURE_MAX_SIZE;
    public static final ForgeConfigSpec.ConfigValue<String> CAPTURE_LIBRARY_DIR;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("Capturing builds into a datapack structure").push("capture");
        CAPTURE_MAX_SIZE = builder
                .comment("Maximum number of blocks per side for /capture save and box editing.")
                .defineInRange("maxSize", 64, 1, 256);
        CAPTURE_LIBRARY_DIR = builder
                .comment("Shared structure library, available in every world.",
                        "Relative paths are resolved against the game directory; absolute paths are used as-is.",
                        "Every /capture save writes a copy here. Set to an empty string to disable the library.")
                .define("libraryDir", "buildcapture_structures");
        builder.pop();

        SPEC = builder.build();
    }

    private BuildCaptureConfig() {
    }
}
