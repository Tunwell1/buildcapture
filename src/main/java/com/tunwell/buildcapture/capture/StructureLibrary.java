package com.tunwell.buildcapture.capture;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
import com.tunwell.buildcapture.template.TemplateExport;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * A structure library shared by every world. Captures are mirrored here on
 * save, and {@code /capture place} and {@code /capture list} fall back to it when
 * the current world has no copy. The folder is laid out like a data pack, so it
 * can also be copied into {@code datapacks} or a mod jar as-is.
 */
public final class StructureLibrary {
    private static final String PACK_META = "{\n"
            + "  \"pack\": {\n"
            + "    \"pack_format\": " + CaptureExporter.PACK_FORMAT + ",\n"
            + "    \"description\": \"Build Capture shared structure library\"\n"
            + "  }\n"
            + "}\n";

    private StructureLibrary() {
    }

    /** Configured library root, or {@code null} when the library is disabled. */
    @Nullable
    public static Path root() {
        String configured = BuildCaptureConfig.CAPTURE_LIBRARY_DIR.get().trim();
        if (configured.isEmpty()) {
            return null;
        }
        try {
            Path path = Paths.get(configured);
            if (!path.isAbsolute()) {
                path = FMLPaths.GAMEDIR.get().resolve(path);
            }
            return path.normalize();
        } catch (InvalidPathException exception) {
            BuildCapture.LOGGER.warn("Invalid capture.libraryDir: {}", configured, exception);
            return null;
        }
    }

    /** File for a structure inside the library, or {@code null} when disabled. */
    @Nullable
    public static Path path(ResourceLocation id) {
        Path root = root();
        if (root == null) {
            return null;
        }
        Path file = root.resolve("data")
                .resolve(id.getNamespace())
                .resolve("structures")
                .resolve(id.getPath() + ".nbt")
                .normalize();
        return file.startsWith(root) ? file : null;
    }

    /** Mirrors one structure into the library; returns the written file. */
    @Nullable
    public static Path write(ResourceLocation id, CompoundNBT nbt) {
        Path file = path(id);
        if (file == null) {
            return null;
        }
        writePackMeta(root());
        return TemplateExport.writeNbt(file, nbt) ? file : null;
    }

    @Nullable
    public static CompoundNBT read(ResourceLocation id) {
        Path file = path(id);
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            return CompressedStreamTools.readCompressed(input);
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to read library structure {}", file, exception);
            return null;
        }
    }

    /** Reads a library structure and parses it through the vanilla datafixers. */
    @Nullable
    public static Template load(MinecraftServer server, ResourceLocation id) {
        CompoundNBT nbt = read(id);
        if (nbt == null) {
            return null;
        }
        try {
            return server.getStructureManager().readStructure(nbt);
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to parse library structure {}", id, exception);
            return null;
        }
    }

    public static boolean delete(ResourceLocation id) {
        Path file = path(id);
        if (file == null) {
            return false;
        }
        return CaptureBackups.delete(file);
    }

    /** All structure ids currently stored in the library. */
    public static List<String> list() {
        Path root = root();
        if (root == null) {
            return Collections.emptyList();
        }
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) {
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(data)) {
            stream.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".nbt"))
                    .forEach(file -> {
                        String id = toId(data, file);
                        if (id != null) {
                            ids.add(id);
                        }
                    });
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to list the structure library in {}", data, exception);
        }
        Collections.sort(ids);
        return ids;
    }

    /** Short path for chat messages. */
    public static String describe(Path path) {
        try {
            Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
            Path absolute = path.toAbsolutePath().normalize();
            Path relative = gameDir.relativize(absolute);
            String text = relative.toString().replace('\\', '/');
            return text.startsWith("..") ? absolute.toString() : text;
        } catch (Exception exception) {
            return path.toString();
        }
    }

    private static void writePackMeta(@Nullable Path root) {
        if (root == null) {
            return;
        }
        Path meta = root.resolve("pack.mcmeta");
        if (Files.exists(meta)) {
            return;
        }
        try {
            Files.createDirectories(root);
            try (OutputStream output = Files.newOutputStream(meta)) {
                output.write(PACK_META.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            BuildCapture.LOGGER.warn("Failed to create {}", meta, exception);
        }
    }

    @Nullable
    private static String toId(Path data, Path file) {
        String relative = data.relativize(file).toString().replace('\\', '/');
        int slash = relative.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        String namespace = relative.substring(0, slash);
        String rest = relative.substring(slash + 1);
        String prefix = "structures/";
        if (!rest.startsWith(prefix) || !rest.endsWith(".nbt")) {
            return null;
        }
        return namespace + ":" + rest.substring(prefix.length(), rest.length() - 4);
    }
}
