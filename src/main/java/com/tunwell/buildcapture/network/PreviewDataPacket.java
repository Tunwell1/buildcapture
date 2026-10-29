package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.core.PreviewPacking;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Block list of one structure for the placement ghost. Positions are packed
 * into 10 bits per axis, so the server only ghosts structures up to 1024 blocks
 * per axis. When the structure is too large to ghost, {@code limited} is set
 * and the palette and block list are empty; the client then keeps the plain
 * footprint box.
 */
public final class PreviewDataPacket {
    /** Defensive limits for a client decoding a reply; they mirror the server caps. */
    private static final int MAX_PALETTE = 2_048;
    private static final int MAX_BLOCKS = 150_000;

    private final String id;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int totalBlocks;
    private final boolean limited;
    private final BlockState[] palette;
    private final int[] positions;
    private final int[] indexes;

    public PreviewDataPacket(String id, int sizeX, int sizeY, int sizeZ, int totalBlocks,
                             boolean limited, BlockState[] palette, int[] positions,
                             int[] indexes) {
        this.id = id;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.totalBlocks = totalBlocks;
        this.limited = limited;
        this.palette = palette;
        this.positions = positions;
        this.indexes = indexes;
    }

    public static void encode(PreviewDataPacket message, PacketBuffer buffer) {
        buffer.writeUtf(message.id, 256);
        buffer.writeVarInt(message.sizeX);
        buffer.writeVarInt(message.sizeY);
        buffer.writeVarInt(message.sizeZ);
        buffer.writeVarInt(message.totalBlocks);
        buffer.writeBoolean(message.limited);

        buffer.writeVarInt(message.palette.length);
        for (BlockState state : message.palette) {
            buffer.writeNbt(NBTUtil.writeBlockState(state));
        }

        buffer.writeVarInt(message.positions.length);
        for (int index = 0; index < message.positions.length; index++) {
            int packed = message.positions[index];
            buffer.writeVarInt(PreviewPacking.x(packed));
            buffer.writeVarInt(PreviewPacking.y(packed));
            buffer.writeVarInt(PreviewPacking.z(packed));
            buffer.writeVarInt(message.indexes[index]);
        }
    }

    public static PreviewDataPacket decode(PacketBuffer buffer) {
        String id = buffer.readUtf(256);
        int sizeX = buffer.readVarInt();
        int sizeY = buffer.readVarInt();
        int sizeZ = buffer.readVarInt();
        int total = buffer.readVarInt();
        boolean limited = buffer.readBoolean();

        int paletteSize = buffer.readVarInt();
        if (paletteSize < 0 || paletteSize > MAX_PALETTE) {
            throw new IllegalArgumentException("Preview palette too large: " + paletteSize);
        }
        BlockState[] palette = new BlockState[paletteSize];
        for (int index = 0; index < palette.length; index++) {
            palette[index] = NBTUtil.readBlockState(buffer.readNbt());
        }

        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_BLOCKS) {
            throw new IllegalArgumentException("Preview block count out of range: " + count);
        }
        int[] positions = new int[count];
        int[] indexes = new int[count];
        for (int index = 0; index < positions.length; index++) {
            int x = buffer.readVarInt();
            int y = buffer.readVarInt();
            int z = buffer.readVarInt();
            positions[index] = PreviewPacking.pack(x, y, z);
            indexes[index] = buffer.readVarInt();
        }
        return new PreviewDataPacket(id, sizeX, sizeY, sizeZ, total, limited,
                palette, positions, indexes);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.tunwell.buildcapture.client.PlacementPreview.setGhost(
                        this.id, this.sizeX, this.sizeY, this.sizeZ, this.totalBlocks,
                        this.limited, this.palette, this.positions, this.indexes)));
        context.setPacketHandled(true);
    }
}
