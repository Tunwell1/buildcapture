package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.capture.StructureImporter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** First packet of an upload: the file name and its exact size in bytes. */
public final class ImportStartPacket {
    private final String name;
    private final int total;

    public ImportStartPacket(String name, int total) {
        this.name = name;
        this.total = total;
    }

    public static void encode(ImportStartPacket message, PacketBuffer buffer) {
        buffer.writeUtf(message.name, 256);
        buffer.writeVarInt(message.total);
    }

    public static ImportStartPacket decode(PacketBuffer buffer) {
        return new ImportStartPacket(buffer.readUtf(256), buffer.readVarInt());
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
                StructureImporter.start(player, this.name, this.total);
            }
        });
        context.setPacketHandled(true);
    }
}
