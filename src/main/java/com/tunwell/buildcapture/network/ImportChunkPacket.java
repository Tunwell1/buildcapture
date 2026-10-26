package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.capture.StructureImporter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** One piece of an uploaded .nbt file. The server expects sequential indexes. */
public final class ImportChunkPacket {
    private final int index;
    private final byte[] data;

    public ImportChunkPacket(int index, byte[] data) {
        this.index = index;
        this.data = data;
    }

    public static void encode(ImportChunkPacket message, PacketBuffer buffer) {
        buffer.writeVarInt(message.index);
        buffer.writeByteArray(message.data);
    }

    public static ImportChunkPacket decode(PacketBuffer buffer) {
        return new ImportChunkPacket(buffer.readVarInt(), buffer.readByteArray());
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
                StructureImporter.chunk(player, this.index, this.data);
            }
        });
        context.setPacketHandled(true);
    }
}
