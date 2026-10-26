package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.FolderName;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Copies of captured structures taken before they are overwritten. Backups live
 * under {@code <world>/buildcapture_backups/<kind>/<namespace>/<path>/} and the
 * newest {@link #KEEP} copies per structure are kept. The kind separates the
 * world datapack copy from the shared library copy of the same structure.
 */
public final class CaptureBackups {
    private static final int KEEP = 20;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private CaptureBackups() {
    }

    @Nullable
    public static Path backup(MinecraftServer server, ResourceLocation id, Path file) {
        return backup(server, id, file, "world");
    }

    @Nullable
    public static Path backup(MinecraftServer server, ResourceLocation id, Path file,
                              String kind) {
        if (!Files.exists(file)) {
            return null;
        }
        Path dir = backupDir(server, id, kind);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(LocalDateTime.now().format(STAMP) + ".nbt");
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            prune(dir);
            return target;
        } catch (IOException exception) {
            BuildCapture.LOGGER.warn("Failed to back up {} to {}", file, dir, exception);
            return null;
        }
    }

    public static boolean restore(Path file, Path backup) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(backup, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException exception) {
            BuildCapture.LOGGER.warn("Failed to restore {} from {}", file, backup, exception);
            return false;
        }
    }

    public static boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException exception) {
            BuildCapture.LOGGER.warn("Failed to delete {}", file, exception);
            return false;
        }
    }

    private static Path backupDir(MinecraftServer server, ResourceLocation id, String kind) {
        return server.getWorldPath(FolderName.ROOT)
                .resolve("buildcapture_backups")
                .resolve(kind)
                .resolve(id.getNamespace())
                .resolve(id.getPath());
    }

    private static void prune(Path dir) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .forEach(files::add);
        } catch (IOException exception) {
            return;
        }
        if (files.size() <= KEEP) {
            return;
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        for (int index = 0; index < files.size() - KEEP; index++) {
            try {
                Files.deleteIfExists(files.get(index));
            } catch (IOException ignored) {
                // A leftover backup is harmless; try again next time.
            }
        }
    }
}
