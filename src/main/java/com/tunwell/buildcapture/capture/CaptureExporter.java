package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
import com.tunwell.buildcapture.template.TemplateExport;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.FolderName;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Turns a world selection into a structure template written to a datapack
 * inside the world folder. Any selection can be captured as {@code ns:path};
 * there is no catalog of known names.
 */
public final class CaptureExporter {
    public static final String DATAPACK_NAME = "buildcapture_captures";
    public static final int PACK_FORMAT = 6;

    private static final String PACK_META = "{\n"
            + "  \"pack\": {\n"
            + "    \"pack_format\": " + PACK_FORMAT + ",\n"
            + "    \"description\": \"Structures captured with Build Capture\"\n"
            + "  }\n"
            + "}\n";

    public enum Status {
        SAVED,
        TOO_LARGE,
        STRUCTURE_BLOCK,
        WRITE_FAILED
    }

    public static final class Result {
        private final Status status;
        @Nullable
        private final ResourceLocation id;
        @Nullable
        private final Path file;
        @Nullable
        private final Path backup;
        @Nullable
        private final Path libraryFile;
        @Nullable
        private final Path libraryBackup;
        private final boolean libraryFailed;
        private final BlockPos size;
        private final int jigsaws;

        private Result(Status status, @Nullable ResourceLocation id, @Nullable Path file,
                       @Nullable Path backup, @Nullable Path libraryFile,
                       @Nullable Path libraryBackup, boolean libraryFailed,
                       BlockPos size, int jigsaws) {
            this.status = status;
            this.id = id;
            this.file = file;
            this.backup = backup;
            this.libraryFile = libraryFile;
            this.libraryBackup = libraryBackup;
            this.libraryFailed = libraryFailed;
            this.size = size;
            this.jigsaws = jigsaws;
        }

        public Status status() {
            return status;
        }

        @Nullable
        public ResourceLocation id() {
            return id;
        }

        @Nullable
        public Path file() {
            return file;
        }

        @Nullable
        public Path backup() {
            return backup;
        }

        @Nullable
        public Path libraryFile() {
            return libraryFile;
        }

        @Nullable
        public Path libraryBackup() {
            return libraryBackup;
        }

        public boolean libraryFailed() {
            return libraryFailed;
        }

        public BlockPos size() {
            return size;
        }

        public int jigsaws() {
            return jigsaws;
        }
    }

    private CaptureExporter() {
    }

    public static Result export(ServerPlayerEntity player, ResourceLocation id,
                                BlockPos first, BlockPos second) {
        BlockPos origin = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        BlockPos size = new BlockPos(
                Math.abs(first.getX() - second.getX()) + 1,
                Math.abs(first.getY() - second.getY()) + 1,
                Math.abs(first.getZ() - second.getZ()) + 1);

        int max = BuildCaptureConfig.CAPTURE_MAX_SIZE.get();
        if (size.getX() > max || size.getY() > max || size.getZ() > max) {
            return new Result(Status.TOO_LARGE, id, null, null, null, null, false, size, 0);
        }

        ServerWorld world = player.getLevel();
        loadChunks(world, origin, size);

        if (TemplateExport.countBlocks(world, origin, size, Blocks.STRUCTURE_BLOCK) > 0) {
            return new Result(Status.STRUCTURE_BLOCK, id, null, null, null, null, false, size, 0);
        }

        int jigsaws = TemplateExport.countJigsaws(world, origin, size);
        Template template = new Template();
        template.fillFromWorld(world, origin, size, true, Blocks.STRUCTURE_VOID);
        template.setAuthor(player.getName().getString());
        CompoundNBT nbt = template.save(new CompoundNBT());

        MinecraftServer server = player.getServer();
        if (!writePackMeta(server)) {
            return new Result(Status.WRITE_FAILED, id, null, null, null, null, false, size, jigsaws);
        }

        Path file = datapackPath(server, id);
        Path backup = CaptureBackups.backup(server, id, file);
        if (!TemplateExport.writeNbt(file, nbt)) {
            return new Result(Status.WRITE_FAILED, id, file, backup, null, null, false, size, jigsaws);
        }

        TemplateExport.updateCache(server, id, nbt);

        // The library is a mirror, so the world datapack stays the source of
        // truth and its backups remain the canonical undo history.
        Path libraryFile = StructureLibrary.path(id);
        Path libraryBackup = null;
        boolean libraryFailed = false;
        if (libraryFile != null) {
            if (Files.exists(libraryFile)) {
                libraryBackup = CaptureBackups.backup(server, id, libraryFile, "library");
                if (libraryBackup == null) {
                    // Never overwrite a library entry we could not back up:
                    // /capture undo would have no way to restore it.
                    libraryFailed = true;
                    libraryFile = null;
                }
            }
            if (libraryFile != null && StructureLibrary.write(id, nbt) == null) {
                libraryFailed = true;
                libraryFile = null;
                libraryBackup = null;
            }
        }
        return new Result(Status.SAVED, id, file, backup, libraryFile, libraryBackup,
                libraryFailed, size, jigsaws);
    }

    public static Path datapackDir(MinecraftServer server) {
        return server.getWorldPath(FolderName.DATAPACK_DIR).resolve(DATAPACK_NAME);
    }

    public static Path datapackPath(MinecraftServer server, ResourceLocation id) {
        return datapackDir(server)
                .resolve("data")
                .resolve(id.getNamespace())
                .resolve("structures")
                .resolve(id.getPath() + ".nbt");
    }

    /** Forces the selection's chunks to generate so the capture is not full of air. */
    private static void loadChunks(ServerWorld world, BlockPos origin, BlockPos size) {
        int minX = origin.getX() >> 4;
        int minZ = origin.getZ() >> 4;
        int maxX = (origin.getX() + size.getX() - 1) >> 4;
        int maxZ = (origin.getZ() + size.getZ() - 1) >> 4;
        for (int chunkX = minX; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                world.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            }
        }
    }

    private static boolean writePackMeta(MinecraftServer server) {
        Path dir = datapackDir(server);
        Path meta = dir.resolve("pack.mcmeta");
        if (Files.exists(meta)) {
            return true;
        }
        try {
            Files.createDirectories(dir);
            Files.write(meta, PACK_META.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException exception) {
            BuildCapture.LOGGER.error("Failed to create {}", meta, exception);
            return false;
        }
    }
}
