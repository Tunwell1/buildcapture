package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.capture.StructureActions;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Asks the server for the block list of a structure so it can be ghosted. */
public final class PreviewRequestPacket {
    private final String id;

    public PreviewRequestPacket(String id) {
        this.id = id;
    }

    public static void encode(PreviewRequestPacket message, PacketBuffer buffer) {
        buffer.writeUtf(message.id, 256);
    }

    public static PreviewRequestPacket decode(PacketBuffer buffer) {
        return new PreviewRequestPacket(buffer.readUtf(256));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayerEntity player = context.getSender();
        if (player == null) {
            context.setPacketHandled(true);
            return;
        }
        context.enqueueWork(() -> {
            if (player.hasPermissions(2)) {
                StructureActions.sendPreview(player, ResourceLocation.tryParse(id));
            }
        });
        context.setPacketHandled(true);
    }
}
