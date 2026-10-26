package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.capture.StructureImporter;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Last packet of an upload: the server validates the file and writes it. */
public final class ImportEndPacket {
    public static void encode(ImportEndPacket message, PacketBuffer buffer) {
    }

    public static ImportEndPacket decode(PacketBuffer buffer) {
        return new ImportEndPacket();
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
                StructureImporter.finish(player);
            }
        });
        context.setPacketHandled(true);
    }
}
