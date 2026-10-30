package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.capture.StructureActions;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** One click in the structure browser: refresh, place, delete or undo. */
public final class StructureActionPacket {
    public enum Action {
        LIST,
        PLACE,
        DELETE,
        UNDO
    }

    private final Action action;
    @Nullable
    private final String id;
    private final int rotation;
    private final BlockPos pos;

    private StructureActionPacket(Action action, @Nullable String id, int rotation, BlockPos pos) {
        this.action = action;
        this.id = id;
        this.rotation = rotation;
        this.pos = pos;
    }

    public static StructureActionPacket list() {
        return new StructureActionPacket(Action.LIST, null, 0, BlockPos.ZERO);
    }

    public static StructureActionPacket place(String id, int rotationSteps, BlockPos pos) {
        return new StructureActionPacket(Action.PLACE, id, rotationSteps, pos);
    }

    public static StructureActionPacket delete(String id) {
        return new StructureActionPacket(Action.DELETE, id, 0, BlockPos.ZERO);
    }

    public static StructureActionPacket undo() {
        return new StructureActionPacket(Action.UNDO, null, 0, BlockPos.ZERO);
    }

    public static void encode(StructureActionPacket message, PacketBuffer buffer) {
        buffer.writeByte(message.action.ordinal());
        buffer.writeUtf(message.id == null ? "" : message.id, 256);
        buffer.writeVarInt(message.rotation);
        buffer.writeBlockPos(message.pos);
    }

    public static StructureActionPacket decode(PacketBuffer buffer) {
        int ordinal = buffer.readUnsignedByte();
        String id = buffer.readUtf(256);
        int rotation = buffer.readVarInt();
        BlockPos pos = buffer.readBlockPos();
        if (ordinal >= Action.values().length) {
            throw new IllegalArgumentException("Unknown structure action: " + ordinal);
        }
        return new StructureActionPacket(Action.values()[ordinal], id.isEmpty() ? null : id,
                rotation, pos);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayerEntity player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            if (!player.hasPermissions(2)) {
                player.sendMessage(new TranslationTextComponent(
                        "buildcapture.capture.need_op"), Util.NIL_UUID);
                return;
            }
            ResourceLocation parsed = id == null ? null : ResourceLocation.tryParse(id);
            switch (action) {
                case LIST:
                    StructureActions.sendList(player);
                    break;
                case PLACE: {
                    StructureActions.PlaceStatus status =
                            StructureActions.place(player, parsed, rotation, pos);
                    switch (status) {
                        case PLACED:
                            player.sendMessage(new TranslationTextComponent(
                                    "buildcapture.capture.placed", String.valueOf(parsed),
                                    pos.getX(), pos.getY(), pos.getZ(),
                                    Math.floorMod(rotation, 4) * 90), Util.NIL_UUID);
                            break;
                        case NOT_FOUND:
                            player.sendMessage(new TranslationTextComponent(
                                    "buildcapture.capture.not_found", String.valueOf(parsed)),
                                    Util.NIL_UUID);
                            break;
                        case UNREADABLE:
                            player.sendMessage(new TranslationTextComponent(
                                    "buildcapture.capture.unreadable", String.valueOf(parsed)),
                                    Util.NIL_UUID);
                            break;
                        case TOO_FAR:
                            player.sendMessage(new TranslationTextComponent(
                                    "buildcapture.capture.too_far"), Util.NIL_UUID);
                            break;
                        case OUT_OF_WORLD:
                            player.sendMessage(new TranslationTextComponent(
                                    "buildcapture.capture.out_of_world"), Util.NIL_UUID);
                            break;
                        default:
                            player.sendMessage(new TranslationTextComponent(
                                    "buildcapture.capture.invalid_name"), Util.NIL_UUID);
                            break;
                    }
                    break;
                }
                case DELETE:
                    StructureActions.delete(player, parsed);
                    break;
                case UNDO:
                    StructureActions.undo(player);
                    break;
                default:
                    break;
            }
        });
        context.setPacketHandled(true);
    }
}
