package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.capture.StructureCatalog;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Sends the structure browser contents from the server to one client. */
public final class StructureListPacket {
    private final List<StructureCatalog.Entry> entries;
    private final String libraryPath;
    private final boolean canUndo;

    public StructureListPacket(List<StructureCatalog.Entry> entries, String libraryPath,
                               boolean canUndo) {
        this.entries = entries;
        this.libraryPath = libraryPath;
        this.canUndo = canUndo;
    }

    public static void encode(StructureListPacket message, PacketBuffer buffer) {
        buffer.writeVarInt(message.entries.size());
        for (StructureCatalog.Entry entry : message.entries) {
            buffer.writeUtf(entry.id().toString());
            buffer.writeBoolean(entry.inWorld());
            buffer.writeBoolean(entry.inLibrary());
            buffer.writeVarInt(entry.size().getX());
            buffer.writeVarInt(entry.size().getY());
            buffer.writeVarInt(entry.size().getZ());
            buffer.writeVarInt((int) Math.min(Integer.MAX_VALUE, entry.bytes()));
        }
        buffer.writeUtf(message.libraryPath, 512);
        buffer.writeBoolean(message.canUndo);
    }

    public static StructureListPacket decode(PacketBuffer buffer) {
        int count = buffer.readVarInt();
        List<StructureCatalog.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ResourceLocation id = ResourceLocation.tryParse(buffer.readUtf(256));
            boolean world = buffer.readBoolean();
            boolean library = buffer.readBoolean();
            int x = buffer.readVarInt();
            int y = buffer.readVarInt();
            int z = buffer.readVarInt();
            int bytes = buffer.readVarInt();
            if (id != null) {
                entries.add(new StructureCatalog.Entry(id, world, library,
                        new BlockPos(x, y, z), bytes));
            }
        }
        String libraryPath = buffer.readUtf(512);
        boolean canUndo = buffer.readBoolean();
        return new StructureListPacket(entries, libraryPath, canUndo);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.tunwell.buildcapture.client.ClientStructureList
                        .set(this.entries, this.libraryPath, this.canUndo)));
        context.setPacketHandled(true);
    }
}
