package com.tunwell.buildcapture.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BuildCaptureConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue CAPTURE_MAX_SIZE;
    public static final ForgeConfigSpec.ConfigValue<String> CAPTURE_LIBRARY_DIR;
    public static final ForgeConfigSpec.BooleanValue CAPTURE_CLEAR_AFTER_SAVE;
    public static final ForgeConfigSpec.IntValue CAPTURE_IMPORT_MAX_MB;

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
        CAPTURE_CLEAR_AFTER_SAVE = builder
                .comment("Drop the selection after a successful /capture save so the box disappears.",
                        "Turn this off if you usually save the same area under several names.")
                .define("clearAfterSave", true);
        CAPTURE_IMPORT_MAX_MB = builder
                .comment("Largest .nbt file the in-game importer accepts, in megabytes.",
                        "Imported files are uploaded from the client in chunks and are written to",
                        "both the world datapack and the shared library.")
                .defineInRange("importMaxMb", 16, 1, 64);
        builder.pop();

        SPEC = builder.build();
    }

    private BuildCaptureConfig() {
    }
}
