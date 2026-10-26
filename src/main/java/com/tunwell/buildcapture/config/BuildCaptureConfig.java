package com.tunwell.buildcapture.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BuildCaptureConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue CAPTURE_MAX_SIZE;
    public static final ForgeConfigSpec.ConfigValue<String> CAPTURE_LIBRARY_DIR;
    public static final ForgeConfigSpec.BooleanValue CAPTURE_CLEAR_AFTER_SAVE;
    public static final ForgeConfigSpec.IntValue CAPTURE_IMPORT_MAX_MB;
    public static final ForgeConfigSpec.IntValue CAPTURE_PREVIEW_MAX_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue CAPTURE_PREVIEW_TRANSLUCENT;

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
        CAPTURE_PREVIEW_MAX_BLOCKS = builder
                .comment("Largest structure the placement preview draws block by block.",
                        "The block list travels in one packet, so the hard ceiling is 150000;",
                        "the default is the ceiling because rendering is cached. Lower it on",
                        "weak clients if a very large preview still stutters or runs out of memory.")
                .defineInRange("previewMaxBlocks", 150_000, 1_000, 150_000);
        CAPTURE_PREVIEW_TRANSLUCENT = builder
                .comment("Draw the placement preview as translucent block models.",
                        "Turn this off for a solid preview; the geometry cost is cached either way,",
                        "so this changes the look and the blending cost, not the rebuild cost.")
                .define("previewTranslucent", true);
        builder.pop();

        SPEC = builder.build();
    }

    private BuildCaptureConfig() {
    }
}
