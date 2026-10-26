package com.tunwell.buildcapture.capture;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Two corners and the most recent export, stored in the player's persistent
 * data so a selection survives a relog.
 */
public final class CaptureSelection {
    private static final String POS1_TAG = "bc_capture_pos1";
    private static final String POS2_TAG = "bc_capture_pos2";
    private static final String LAST_ID_TAG = "bc_capture_last_id";
    private static final String LAST_FILE_TAG = "bc_capture_last_file";
    private static final String LAST_BACKUP_TAG = "bc_capture_last_backup";
    private static final String LAST_LIBRARY_FILE_TAG = "bc_capture_last_library_file";
    private static final String LAST_LIBRARY_BACKUP_TAG = "bc_capture_last_library_backup";

    private CaptureSelection() {
    }

    public static void setCorner(ServerPlayerEntity player, int corner, BlockPos pos) {
        player.getPersistentData().putLong(corner == 1 ? POS1_TAG : POS2_TAG, pos.asLong());
    }

    /** Stores two corners as a normalised min/max pair. */
    public static void setBounds(ServerPlayerEntity player, BlockPos first, BlockPos second) {
        setCorner(player, 1, new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ())));
        setCorner(player, 2, new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ())));
    }

    @Nullable
    public static BlockPos corner(ServerPlayerEntity player, int corner) {
        CompoundNBT data = player.getPersistentData();
        String tag = corner == 1 ? POS1_TAG : POS2_TAG;
        return data.contains(tag, 4) ? BlockPos.of(data.getLong(tag)) : null;
    }

    public static void clear(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        data.remove(POS1_TAG);
        data.remove(POS2_TAG);
    }

    public static void remember(ServerPlayerEntity player, ResourceLocation id,
                                Path file, @Nullable Path backup,
                                @Nullable Path libraryFile, @Nullable Path libraryBackup) {
        CompoundNBT data = player.getPersistentData();
        data.putString(LAST_ID_TAG, id.toString());
        data.putString(LAST_FILE_TAG, file.toString());
        if (backup == null) {
            data.remove(LAST_BACKUP_TAG);
        } else {
            data.putString(LAST_BACKUP_TAG, backup.toString());
        }
        if (libraryFile == null) {
            data.remove(LAST_LIBRARY_FILE_TAG);
        } else {
            data.putString(LAST_LIBRARY_FILE_TAG, libraryFile.toString());
        }
        if (libraryBackup == null) {
            data.remove(LAST_LIBRARY_BACKUP_TAG);
        } else {
            data.putString(LAST_LIBRARY_BACKUP_TAG, libraryBackup.toString());
        }
    }

    @Nullable
    public static Last last(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        if (!data.contains(LAST_ID_TAG, 8) || !data.contains(LAST_FILE_TAG, 8)) {
            return null;
        }
        String id = data.getString(LAST_ID_TAG);
        Path file = Paths.get(data.getString(LAST_FILE_TAG));
        Path backup = data.contains(LAST_BACKUP_TAG, 8)
                ? Paths.get(data.getString(LAST_BACKUP_TAG))
                : null;
        Path libraryFile = data.contains(LAST_LIBRARY_FILE_TAG, 8)
                ? Paths.get(data.getString(LAST_LIBRARY_FILE_TAG))
                : null;
        Path libraryBackup = data.contains(LAST_LIBRARY_BACKUP_TAG, 8)
                ? Paths.get(data.getString(LAST_LIBRARY_BACKUP_TAG))
                : null;
        return new Last(id, file, backup, libraryFile, libraryBackup);
    }

    public static void forget(ServerPlayerEntity player) {
        CompoundNBT data = player.getPersistentData();
        data.remove(LAST_ID_TAG);
        data.remove(LAST_FILE_TAG);
        data.remove(LAST_BACKUP_TAG);
        data.remove(LAST_LIBRARY_FILE_TAG);
        data.remove(LAST_LIBRARY_BACKUP_TAG);
    }

    public static final class Last {
        private final String id;
        private final Path file;
        @Nullable
        private final Path backup;
        @Nullable
        private final Path libraryFile;
        @Nullable
        private final Path libraryBackup;

        private Last(String id, Path file, @Nullable Path backup,
                     @Nullable Path libraryFile, @Nullable Path libraryBackup) {
            this.id = id;
            this.file = file;
            this.backup = backup;
            this.libraryFile = libraryFile;
            this.libraryBackup = libraryBackup;
        }

        public String id() {
            return id;
        }

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
    }
}
