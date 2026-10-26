package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.command.CaptureCommand;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A capture request from a key binding: set one corner from the block under the
 * crosshair, or save the current selection under a name.
 *
 * <p>These go through {@link CaptureCommand}, so the server keeps owning the
 * selection, the permission check and the wording of every message.</p>
 */
public final class CaptureCommandPacket {
    public static final int CORNER1 = 0;
    public static final int CORNER2 = 1;
    public static final int SAVE = 2;

    private final int operation;
    private final String name;

    public CaptureCommandPacket(int operation, String name) {
        this.operation = operation;
        this.name = name == null ? "" : name;
    }

    public static void encode(CaptureCommandPacket message, PacketBuffer buffer) {
        buffer.writeVarInt(message.operation);
        buffer.writeUtf(message.name, 128);
    }

    public static CaptureCommandPacket decode(PacketBuffer buffer) {
        return new CaptureCommandPacket(buffer.readVarInt(), buffer.readUtf(128));
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
                player.displayClientMessage(new TranslationTextComponent(
                        "buildcapture.capture.need_op"), false);
                return;
            }
            CaptureCommand.handleClientRequest(player, operation, name);
        });
        context.setPacketHandled(true);
    }
}
