package com.tunwell.buildcapture.command;

import com.tunwell.buildcapture.BuildCapture;
import com.tunwell.buildcapture.capture.CaptureBackups;
import com.tunwell.buildcapture.capture.CaptureExporter;
import com.tunwell.buildcapture.capture.CaptureSelection;
import com.tunwell.buildcapture.capture.CaptureUndo;
import com.tunwell.buildcapture.capture.StructureActions;
import com.tunwell.buildcapture.capture.StructureCatalog;
import com.tunwell.buildcapture.capture.StructureLibrary;
import com.tunwell.buildcapture.config.BuildCaptureConfig;
import com.tunwell.buildcapture.network.CaptureBoxPacket;
import com.tunwell.buildcapture.network.CaptureCommandPacket;
import com.tunwell.buildcapture.network.ModNetwork;
import com.tunwell.buildcapture.network.OpenGuiPacket;
import com.tunwell.buildcapture.template.TemplateExport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.resources.ResourcePackList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.PacketDistributor;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * {@code /capture}: select two corners of a build and write it to a datapack as
 * a structure template, then place, list and undo captures.
 */
@Mod.EventBusSubscriber(modid = BuildCapture.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureCommand {
    private static final int MAX_LISTED = 40;
    private static final String[] DIRECTIONS = {"north", "south", "east", "west", "up", "down"};

    private enum Adjust {
        EXPAND,
        SHRINK,
        MOVE
    }

    private CaptureCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            sendBox((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            resendBoxLater((ServerPlayerEntity) event.getPlayer());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            // A selection is a set of coordinates in one dimension; keeping it
            // after a portal trip would save the wrong place in the new world.
            ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            CaptureSelection.clear(player);
            sendBox(player);
        }
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("capture")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("pos1")
                        .executes(context -> setCorner(context.getSource(), 1)))
                .then(Commands.literal("pos2")
                        .executes(context -> setCorner(context.getSource(), 2)))
                .then(directionGroup("expand", Adjust.EXPAND))
                .then(directionGroup("shrink", Adjust.SHRINK))
                .then(directionGroup("move", Adjust.MOVE))
                .then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> save(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("place")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(CaptureCommand::suggestIds)
                                .executes(context -> placeNamed(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(CaptureCommand::suggestIds)
                                .executes(context -> info(context.getSource(),
                                        StringArgumentType.getString(context, "name")))))
                .then(Commands.literal("gui")
                        .executes(context -> openGui(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("undo")
                        .executes(context -> undo(context.getSource())))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context.getSource())))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource()))));
    }

    public static void refresh(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.getRoot().getChildren().removeIf(node -> "capture".equals(node.getName()));
        register(dispatcher);
    }

    private static int openGui(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        if (!ModNetwork.CHANNEL.isRemotePresent(player.connection.connection)) {
            source.sendFailure(Messages.forSource(source, "buildcapture.capture.gui_needs_mod"));
            return 0;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenGuiPacket());
        return 1;
    }

    /**
     * Entry point for the key bindings. The packet handler has already checked
     * the permission level; everything after this is the same code the commands
     * run, so the selection checks and the messages cannot drift apart.
     */
    public static void handleClientRequest(ServerPlayerEntity player, int operation, String name) {
        CommandSource source = player.createCommandSourceStack();
        try {
            switch (operation) {
                case CaptureCommandPacket.CORNER1:
                    setCorner(source, 1);
                    break;
                case CaptureCommandPacket.CORNER2:
                    setCorner(source, 2);
                    break;
                case CaptureCommandPacket.SAVE:
                    save(source, name);
                    break;
                default:
                    break;
            }
        } catch (CommandSyntaxException exception) {
            BuildCapture.LOGGER.debug("Ignored an incomplete capture request", exception);
        }
    }

    public static int setCorner(CommandSource source, int corner) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        BlockPos pos = targetedBlock(player);
        if (pos == null) {
            source.sendFailure(Messages.forSource(source, 
                    "buildcapture.capture.aim_at_block"));
            return 0;
        }
        CaptureSelection.setCorner(player, corner, pos);
        sendBox(player);
        source.sendSuccess(Messages.forSource(source, 
                corner == 1 ? "buildcapture.capture.pos1" : "buildcapture.capture.pos2",
                pos.getX(), pos.getY(), pos.getZ()), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSource> directionGroup(String name, Adjust mode) {
        LiteralArgumentBuilder<CommandSource> group = Commands.literal(name);
        for (String direction : DIRECTIONS) {
            group.then(Commands.literal(direction)
                    .executes(context -> adjust(context.getSource(), mode, direction, 1))
                    .then(Commands.argument("blocks", IntegerArgumentType.integer(1, 256))
                            .executes(context -> adjust(context.getSource(), mode, direction,
                                    IntegerArgumentType.getInteger(context, "blocks")))));
        }
        return group;
    }

    /**
     * Grows, shrinks or moves the selection without touching the corners again.
     * The box is normalised to a min/max pair, so "north" always means -Z no
     * matter which corner was set first.
     */
    private static int adjust(CommandSource source, Adjust mode, String direction, int blocks)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        BlockPos first = CaptureSelection.corner(player, 1);
        BlockPos second = CaptureSelection.corner(player, 2);
        if (first == null || second == null) {
            source.sendFailure(Messages.forSource(source, "buildcapture.capture.no_selection"));
            return 0;
        }

        int[] offset = directionOffset(direction);
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());

        switch (mode) {
            case EXPAND:
                if (offset[0] > 0) {
                    maxX += blocks;
                } else if (offset[0] < 0) {
                    minX -= blocks;
                }
                if (offset[1] > 0) {
                    maxY += blocks;
                } else if (offset[1] < 0) {
                    minY -= blocks;
                }
                if (offset[2] > 0) {
                    maxZ += blocks;
                } else if (offset[2] < 0) {
                    minZ -= blocks;
                }
                break;
            case SHRINK:
                if (offset[0] > 0) {
                    maxX -= blocks;
                } else if (offset[0] < 0) {
                    minX += blocks;
                }
                if (offset[1] > 0) {
                    maxY -= blocks;
                } else if (offset[1] < 0) {
                    minY += blocks;
                }
                if (offset[2] > 0) {
                    maxZ -= blocks;
                } else if (offset[2] < 0) {
                    minZ += blocks;
                }
                break;
            default:
                minX += offset[0] * blocks;
                maxX += offset[0] * blocks;
                minY += offset[1] * blocks;
                maxY += offset[1] * blocks;
                minZ += offset[2] * blocks;
                maxZ += offset[2] * blocks;
                break;
        }

        int wantedY = maxY - minY + 1;
        int top = player.getLevel().dimensionType().logicalHeight() - 1;
        minY = Math.max(0, minY);
        maxY = Math.min(top, maxY);
        if (maxY - minY + 1 < wantedY) {
            source.sendFailure(Messages.forSource(source, 
                    "buildcapture.capture.out_of_bounds", top));
            return 0;
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            source.sendFailure(Messages.forSource(source, 
                    "buildcapture.capture.too_small", blocks,
                    directionText(direction)));
            return 0;
        }

        CaptureSelection.setCorner(player, 1, new BlockPos(minX, minY, minZ));
        CaptureSelection.setCorner(player, 2, new BlockPos(maxX, maxY, maxZ));
        sendBox(player);

        String key = mode == Adjust.EXPAND ? "buildcapture.capture.expanded"
                : mode == Adjust.SHRINK ? "buildcapture.capture.shrunk"
                        : "buildcapture.capture.moved";
        source.sendSuccess(Messages.forSource(source, key, blocks,
                directionText(direction), sizeX, sizeY, sizeZ), false);
        return 1;
    }

    /** Unit vector for a direction name, using Minecraft's axes (north is -Z). */
    private static int[] directionOffset(String direction) {
        switch (direction) {
            case "north":
                return new int[]{0, 0, -1};
            case "south":
                return new int[]{0, 0, 1};
            case "east":
                return new int[]{1, 0, 0};
            case "west":
                return new int[]{-1, 0, 0};
            case "up":
                return new int[]{0, 1, 0};
            case "down":
                return new int[]{0, -1, 0};
            default:
                throw new IllegalArgumentException("Unknown direction " + direction);
        }
    }

    private static TranslationTextComponent directionText(String direction) {
        return new TranslationTextComponent("buildcapture.capture.dir." + direction);
    }

    public static int save(CommandSource source, String rawName) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        ResourceLocation id = parseId(rawName);
        if (id == null) {
            source.sendFailure(Messages.forSource(source, "buildcapture.capture.invalid_name"));
            return 0;
        }

        BlockPos first = CaptureSelection.corner(player, 1);
        BlockPos second = CaptureSelection.corner(player, 2);
        if (first == null || second == null) {
            source.sendFailure(Messages.forSource(source, "buildcapture.capture.no_selection"));
            return 0;
        }

        CaptureExporter.Result result = CaptureExporter.export(player, id, first, second);
        switch (result.status()) {
            case SAVED: {
                CaptureSelection.remember(player, id, result.file(), result.backup(),
                        result.libraryFile(), result.libraryBackup());
                BlockPos size = result.size();
                source.sendSuccess(Messages.forSource(source, 
                        "buildcapture.capture.saved", id.toString(),
                        size.getX(), size.getY(), size.getZ(), result.jigsaws(),
                        relative(source.getServer(), result.file())), false);
                if (result.libraryFile() != null) {
                    source.sendSuccess(Messages.forSource(source, 
                            "buildcapture.capture.library_saved",
                            StructureLibrary.describe(result.libraryFile())), false);
                } else if (result.libraryFailed()) {
                    source.sendSuccess(Messages.forSource(source, 
                            "buildcapture.capture.library_failed"), false);
                }
                source.sendSuccess(Messages.forSource(source, 
                        "buildcapture.capture.saved_hint", id.toString()), false);
                if (BuildCaptureConfig.CAPTURE_CLEAR_AFTER_SAVE.get()) {
                    CaptureSelection.clear(player);
                    sendBox(player);
                }
                return 1;
            }
            case STRUCTURE_BLOCK:
                source.sendFailure(Messages.forSource(source, 
                        "buildcapture.capture.structure_block"));
                return 0;
            default:
                source.sendFailure(Messages.forSource(source, 
                        "buildcapture.capture.failed", id.toString()));
                return 0;
        }
    }

    private static int place(CommandSource source, String rawName, Rotation rotation)
            throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        ResourceLocation id = parseId(rawName);
        if (id == null) {
            source.sendFailure(Messages.forSource(source, "buildcapture.capture.invalid_name"));
            return 0;
        }

        BlockPos origin = player.blockPosition().relative(player.getDirection(), 2).below();
        int steps = rotationSteps(rotation);
        switch (StructureActions.place(player, id, steps, origin)) {
            case PLACED:
                source.sendSuccess(Messages.forSource(source, 
                        "buildcapture.capture.placed", id.toString(),
                        origin.getX(), origin.getY(), origin.getZ(), steps * 90), false);
                return 1;
            case NOT_FOUND:
                source.sendFailure(Messages.forSource(source, 
                        "buildcapture.capture.not_found", id.toString()));
                return 0;
            case UNREADABLE:
                source.sendFailure(Messages.forSource(source,
                        "buildcapture.capture.unreadable", id.toString()));
                return 0;
            case TOO_FAR:
                source.sendFailure(Messages.forSource(source, 
                        "buildcapture.capture.too_far"));
                return 0;
            case OUT_OF_WORLD:
                source.sendFailure(Messages.forSource(source, 
                        "buildcapture.capture.out_of_world"));
                return 0;
            default:
                source.sendFailure(Messages.forSource(source, 
                        "buildcapture.capture.invalid_name"));
                return 0;
        }
    }

    /**
     * {@code /capture place <name> [90|180|270]}. The name is a greedy argument so
     * that namespaced and folder ids can be typed and suggested, which means the
     * rotation arrives as part of the same string and is split back off here.
     */
    private static int placeNamed(CommandSource source, String raw) throws CommandSyntaxException {
        String name = raw.trim();
        Rotation rotation = Rotation.NONE;
        int space = name.lastIndexOf(' ');
        if (space >= 0) {
            switch (name.substring(space + 1)) {
                case "90":
                    rotation = Rotation.CLOCKWISE_90;
                    break;
                case "180":
                    rotation = Rotation.CLOCKWISE_180;
                    break;
                case "270":
                    rotation = Rotation.COUNTERCLOCKWISE_90;
                    break;
                default:
                    break;
            }
            if (rotation != Rotation.NONE) {
                name = name.substring(0, space);
            }
        }
        return place(source, name, rotation);
    }

    private static int rotationSteps(Rotation rotation) {
        switch (rotation) {
            case CLOCKWISE_90:
                return 1;
            case CLOCKWISE_180:
                return 2;
            case COUNTERCLOCKWISE_90:
                return 3;
            default:
                return 0;
        }
    }

    private static int list(CommandSource source) {
        List<String> worldIds = collectIds(
                CaptureExporter.datapackDir(source.getServer()).resolve("data"));
        List<String> libraryIds = StructureLibrary.list();

        Set<String> all = new TreeSet<>(worldIds);
        all.addAll(libraryIds);
        if (all.isEmpty()) {
            source.sendSuccess(Messages.forSource(source, 
                    "buildcapture.capture.list_empty"), false);
            return 0;
        }

        List<String> ids = new ArrayList<>(all);
        List<String> shown = new ArrayList<>();
        boolean fromLibrary = false;
        for (String id : ids.subList(0, Math.min(ids.size(), MAX_LISTED))) {
            if (worldIds.contains(id)) {
                shown.add(id);
            } else {
                shown.add(id + "*");
                fromLibrary = true;
            }
        }
        source.sendSuccess(Messages.forSource(source, 
                "buildcapture.capture.list", ids.size(), String.join(", ", shown)), false);
        if (fromLibrary) {
            source.sendSuccess(Messages.forSource(source, 
                    "buildcapture.capture.list_hint"), false);
        }
        return ids.size();
    }

    private static CompletableFuture<Suggestions> suggestIds(CommandContext<CommandSource> context,
                                                             SuggestionsBuilder builder) {
        for (String id : allIds(context.getSource().getServer())) {
            if (id.startsWith(builder.getRemaining().toLowerCase(Locale.ROOT))) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    /** Every structure id the browser and the commands can see, world first. */
    private static List<String> allIds(@Nullable MinecraftServer server) {
        Set<String> ids = new TreeSet<>();
        if (server != null) {
            ids.addAll(collectIds(CaptureExporter.datapackDir(server).resolve("data")));
        }
        ids.addAll(StructureLibrary.list());
        return new ArrayList<>(ids);
    }

    private static int info(CommandSource source, String rawName) {
        ResourceLocation id = parseId(rawName);
        if (id == null) {
            source.sendFailure(Messages.forSource(source, "buildcapture.capture.invalid_name"));
            return 0;
        }
        Path world = CaptureExporter.datapackPath(source.getServer(), id);
        Path library = StructureLibrary.path(id);
        boolean haveWorld = world != null && Files.isRegularFile(world);
        boolean haveLibrary = library != null && Files.isRegularFile(library);
        if (!haveWorld && !haveLibrary) {
            source.sendFailure(Messages.forSource(source,
                    "buildcapture.capture.not_found", id.toString()));
            return 0;
        }

        Path file = haveWorld ? world : library;
        BlockPos size = StructureCatalog.sizeOf(file);
        if (size.getX() <= 0) {
            source.sendFailure(Messages.forSource(source,
                    "buildcapture.capture.unreadable", id.toString()));
            return 0;
        }
        long kilobytes = Math.max(1L, bytesOf(file) / 1024L);
        source.sendSuccess(Messages.forSource(source, "buildcapture.capture.info",
                id.toString(), size.getX(), size.getY(), size.getZ(), kilobytes), false);
        if (haveWorld) {
            source.sendSuccess(Messages.forSource(source, "buildcapture.capture.info_world",
                    relative(source.getServer(), world)), false);
        }
        if (haveLibrary) {
            source.sendSuccess(Messages.forSource(source, "buildcapture.capture.info_library",
                    StructureLibrary.describe(library)), false);
        }
        return 1;
    }

    private static long bytesOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static List<String> collectIds(Path data) {
        List<String> ids = new ArrayList<>();
        if (!Files.isDirectory(data)) {
            return ids;
        }
        try (Stream<Path> stream = Files.walk(data)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".nbt"))
                    .forEach(path -> {
                        String id = StructureLibrary.toId(data, path);
                        if (id != null) {
                            ids.add(id);
                        }
                    });
        } catch (IOException exception) {
            BuildCapture.LOGGER.warn("Failed to list captured structures in {}", data, exception);
        }
        return ids;
    }

    private static int undo(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        CaptureUndo.Result result = CaptureUndo.undo(player);
        if (result.status() == CaptureUndo.Status.NOTHING) {
            source.sendFailure(Messages.forSource(source, 
                    "buildcapture.capture.nothing_to_undo"));
            return 0;
        }
        if (result.status() == CaptureUndo.Status.FAILED) {
            source.sendFailure(Messages.forSource(source, 
                    "buildcapture.capture.undo_failed", result.id()));
            return 0;
        }
        boolean restored = result.status() == CaptureUndo.Status.RESTORED;
        source.sendSuccess(Messages.forSource(source, 
                restored ? "buildcapture.capture.undo_restored"
                        : "buildcapture.capture.undo_removed",
                result.id()), false);
        return 1;
    }

    private static int clear(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        CaptureSelection.clear(player);
        sendBox(player);
        source.sendSuccess(Messages.forSource(source, 
                "buildcapture.capture.cleared"), false);
        return 1;
    }

    private static int status(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        BlockPos first = CaptureSelection.corner(player, 1);
        BlockPos second = CaptureSelection.corner(player, 2);
        sendBox(player);
        if (first == null && second == null) {
            source.sendSuccess(Messages.forSource(source, 
                    "buildcapture.capture.status_empty"), false);
            return 0;
        }

        String left = first == null ? "?" : describe(first);
        String right = second == null ? "?" : describe(second);
        String size = first == null || second == null
                ? "?"
                : (Math.abs(first.getX() - second.getX()) + 1) + "x"
                        + (Math.abs(first.getY() - second.getY()) + 1) + "x"
                        + (Math.abs(first.getZ() - second.getZ()) + 1);
        source.sendSuccess(Messages.forSource(source, 
                "buildcapture.capture.status", left, right, size), false);
        Path library = StructureLibrary.root();
        if (library == null) {
            source.sendSuccess(Messages.forSource(source, 
                    "buildcapture.capture.library_disabled"), false);
        } else {
            source.sendSuccess(Messages.forSource(source, 
                    "buildcapture.capture.library_status",
                    StructureLibrary.describe(library)), false);
        }
        if (first != null && second != null) {
            source.sendSuccess(Messages.forSource(source, 
                    "buildcapture.capture.drag_hint"), false);
        }
        return 1;
    }

    private static int reload(CommandSource source) {
        MinecraftServer server = source.getServer();
        ResourcePackList repository = server.getPackRepository();
        repository.reload();

        String packId = "file/" + CaptureExporter.DATAPACK_NAME;
        Collection<String> selected = new LinkedHashSet<>(repository.getSelectedIds());
        if (repository.isAvailable(packId)) {
            selected.add(packId);
        }
        server.reloadResources(selected);
        TemplateExport.clearCache();
        source.sendSuccess(Messages.forSource(source, 
                "buildcapture.capture.reloaded", packId), false);
        return 1;
    }

    @Nullable
    private static ResourceLocation parseId(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty()) {
            return null;
        }
        String qualified = name.indexOf(':') >= 0 ? name : BuildCapture.MOD_ID + ":" + name;
        ResourceLocation id = ResourceLocation.tryParse(qualified);
        if (id == null) {
            return null;
        }
        // ResourceLocation allows dots and slashes, so reject traversal segments
        // before the path is used to build a file location.
        return CaptureExporter.isSafeId(id) ? id : null;
    }

    private static String relative(MinecraftServer server, Path path) {
        try {
            return server.getWorldPath(FolderName.ROOT).relativize(path).toString().replace('\\', '/');
        } catch (Exception exception) {
            return path.toString();
        }
    }

    private static String describe(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** Keeps the client-side outline in sync with the server's selection. */
    public static void sendBox(ServerPlayerEntity player) {
        // A client without the mod accepts the connection but has no channel to
        // read this packet on; skip it instead of sending an unknown payload.
        if (!ModNetwork.CHANNEL.isRemotePresent(player.connection.connection)) {
            return;
        }
        BlockPos first = CaptureSelection.corner(player, 1);
        BlockPos second = CaptureSelection.corner(player, 2);
        CaptureBoxPacket packet = first == null || second == null
                ? CaptureBoxPacket.clear()
                : CaptureBoxPacket.box(first, second);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * After a respawn or a dimension change the client drops its world, so the
     * outline has to be sent once the new world is ready.
     */
    private static void resendBoxLater(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.execute(() -> {
                if (player.isAlive() && !player.hasDisconnected()) {
                    sendBox(player);
                }
            });
        }
    }

    @Nullable
    private static BlockPos targetedBlock(ServerPlayerEntity player) {
        RayTraceResult hit = player.pick(16.0D, 0.0F, false);
        if (hit.getType() == RayTraceResult.Type.BLOCK) {
            return ((BlockRayTraceResult) hit).getBlockPos();
        }
        return null;
    }
}
