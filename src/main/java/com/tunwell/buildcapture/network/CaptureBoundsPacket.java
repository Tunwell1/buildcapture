package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.capture.CaptureBounds;
import com.tunwell.buildcapture.command.CaptureCommand;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server: the selection bounds after one step of an interactive
 * drag. The server clamps the vertical extent to the build height and echoes
 * the result with {@link CaptureBoxPacket}.
 */
public final class CaptureBoundsPacket {
    private final BlockPos min;
    private final BlockPos max;
    private final int axis;
    private final int side;

    public CaptureBoundsPacket(BlockPos min, BlockPos max, int axis, int side) {
        this.min = min;
        this.max = max;
        this.axis = axis;
        this.side = side;
    }

    public static void encode(CaptureBoundsPacket message, PacketBuffer buffer) {
        buffer.writeBlockPos(message.min);
        buffer.writeBlockPos(message.max);
        buffer.writeByte(message.axis);
        buffer.writeByte(message.side);
    }

    public static CaptureBoundsPacket decode(PacketBuffer buffer) {
        return new CaptureBoundsPacket(buffer.readBlockPos(), buffer.readBlockPos(),
                buffer.readByte(), buffer.readByte());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayerEntity player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> {
                CaptureBounds.apply(player, this.min, this.max, this.axis, this.side);
                // Always echo the authoritative box so the client snaps back if
                // the server clamped or rejected the step.
                CaptureCommand.sendBox(player);
            });
        }
        context.setPacketHandled(true);
    }
}
