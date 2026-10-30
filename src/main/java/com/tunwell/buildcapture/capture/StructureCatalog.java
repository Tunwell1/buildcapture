package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.ListNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Everything the in-game browser shows: captures from the world datapack and
 * from the shared library, with their dimensions and file size. The header of
 * each file is read once and cached until the file changes, because opening the
 * browser should not re-parse a hundred structures.
 */
public final class StructureCatalog {
    private static final Map<String, Cached> CACHE = new ConcurrentHashMap<>();

    private StructureCatalog() {
    }

    public static final class Entry {
        private final ResourceLocation id;
        private final boolean world;
        private final boolean library;
        private final BlockPos size;
        private final long bytes;

        public Entry(ResourceLocation id, boolean world, boolean library, BlockPos size,
                     long bytes) {
            this.id = id;
            this.world = world;
            this.library = library;
            this.size = size;
            this.bytes = bytes;
        }

        public ResourceLocation id() {
            return id;
        }

        public boolean inWorld() {
            return world;
        }

        public boolean inLibrary() {
            return library;
        }

        public BlockPos size() {
            return size;
        }

        public long bytes() {
            return bytes;
        }
    }

    public static List<Entry> scan(MinecraftServer server) {
        Map<String, Entry> byId = new LinkedHashMap<>();

        Path worldData = CaptureExporter.datapackDir(server).resolve("data");
        for (Path file : collect(worldData)) {
            String text = StructureLibrary.toId(worldData, file);
            ResourceLocation id = text == null ? null : ResourceLocation.tryParse(text);
            if (id == null) {
                continue;
            }
            byId.put(id.toString(), new Entry(id, true, false, sizeOf(file), lengthOf(file)));
        }

        for (String text : StructureLibrary.list()) {
            ResourceLocation id = ResourceLocation.tryParse(text);
            if (id == null) {
                continue;
            }
            Path file = StructureLibrary.path(id);
            if (file == null || !Files.isRegularFile(file)) {
                continue;
            }
            Entry world = byId.get(id.toString());
            byId.put(id.toString(), new Entry(id, world != null, true,
                    sizeOf(file), lengthOf(file)));
        }

        List<Entry> entries = new ArrayList<>(byId.values());
        entries.sort(Comparator.comparing(entry -> entry.id().toString()));
        return entries;
    }

    private static List<Path> collect(Path data) {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(data)) {
            return files;
        }
        try (Stream<Path> stream = Files.walk(data)) {
            stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".nbt"))
                    .forEach(files::add);
        } catch (IOException exception) {
            BuildCapture.LOGGER.warn("Failed to list structures in {}", data, exception);
        }
        return files;
    }

    private static long lengthOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            return 0L;
        }
    }

    /** Dimensions from the structure header, or {@link BlockPos#ZERO} when unreadable. */
    public static BlockPos sizeOf(Path file) {
        long modified;
        long length;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
            length = Files.size(file);
        } catch (IOException exception) {
            return BlockPos.ZERO;
        }

        String key = file.toAbsolutePath().toString();
        Cached cached = CACHE.get(key);
        if (cached != null && cached.modified == modified && cached.length == length) {
            return cached.size;
        }

        BlockPos size = readSize(file);
        CACHE.put(key, new Cached(modified, length, size));
        return size;
    }

    private static BlockPos readSize(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            CompoundNBT nbt = CompressedStreamTools.readCompressed(input);
            ListNBT list = nbt.getList("size", 3);
            if (list.size() == 3) {
                int x = list.getInt(0);
                int y = list.getInt(1);
                int z = list.getInt(2);
                if (x > 0 && y > 0 && z > 0) {
                    return new BlockPos(x, y, z);
                }
            }
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to read the size of {}", file, exception);
        }
        return BlockPos.ZERO;
    }

    private static final class Cached {
        private final long modified;
        private final long length;
        private final BlockPos size;

        private Cached(long modified, long length, BlockPos size) {
            this.modified = modified;
            this.length = length;
            this.size = size;
        }
    }
}
