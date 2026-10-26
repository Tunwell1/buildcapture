package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.template.TemplateExport;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reverts the last file operation of one player: restores a previous world or
 * library copy from its backup, or deletes a file that the operation created.
 * Shared by {@code /capture undo} and the browser button so the two cannot
 * drift apart.
 */
public final class CaptureUndo {
    public enum Status {
        NOTHING,
        RESTORED,
        REMOVED,
        FAILED
    }

    public static final class Result {
        private final Status status;
        @Nullable
        private final String id;

        private Result(Status status, @Nullable String id) {
            this.status = status;
            this.id = id;
        }

        public Status status() {
            return status;
        }

        /** The capture id, or the raw stored text when it no longer parses. */
        @Nullable
        public String id() {
            return id;
        }
    }

    private CaptureUndo() {
    }

    public static Result undo(ServerPlayerEntity player) {
        CaptureSelection.Last last = CaptureSelection.last(player);
        if (last == null) {
            return new Result(Status.NOTHING, null);
        }

        MinecraftServer server = player.getServer();
        ResourceLocation id = ResourceLocation.tryParse(last.id());
        if (server == null || id == null) {
            CaptureSelection.forget(player);
            return new Result(Status.FAILED, last.id());
        }

        Path backup = last.backup();
        boolean restored;
        if (backup != null && Files.exists(backup)) {
            if (!CaptureBackups.restore(last.file(), backup)) {
                return new Result(Status.FAILED, id.toString());
            }
            restoreCache(server, id, backup);
            restored = true;
        } else {
            CaptureBackups.delete(last.file());
            TemplateExport.forget(server, id);
            restored = false;
        }

        undoLibrary(last);
        CaptureSelection.forget(player);
        return new Result(restored ? Status.RESTORED : Status.REMOVED, id.toString());
    }

    /** Restores the library copy to the state it had before the capture. */
    private static void undoLibrary(CaptureSelection.Last last) {
        Path libraryFile = last.libraryFile();
        if (libraryFile == null) {
            return;
        }
        Path libraryBackup = last.libraryBackup();
        if (libraryBackup != null && Files.exists(libraryBackup)) {
            CaptureBackups.restore(libraryFile, libraryBackup);
        } else {
            CaptureBackups.delete(libraryFile);
        }
    }

    private static void restoreCache(MinecraftServer server, ResourceLocation id, Path backup) {
        try (InputStream input = Files.newInputStream(backup)) {
            CompoundNBT nbt = CompressedStreamTools.readCompressed(input);
            TemplateExport.updateCache(server, id, nbt);
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to refresh the cached template {}", id, exception);
        }
    }
}
