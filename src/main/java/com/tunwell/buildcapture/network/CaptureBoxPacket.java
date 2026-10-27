package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.client.ClientCaptureBox;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sends the current capture selection to the owning client for rendering and
 * interactive editing.
 */
public final class CaptureBoxPacket {
    private final boolean visible;
    private final BlockPos first;
    private final BlockPos second;

    private CaptureBoxPacket(boolean visible, BlockPos first, BlockPos second) {
        this.visible = visible;
        this.first = first;
        this.second = second;
    }

    public static CaptureBoxPacket box(BlockPos first, BlockPos second) {
        return new CaptureBoxPacket(true, first, second);
    }

    public static CaptureBoxPacket clear() {
        return new CaptureBoxPacket(false, BlockPos.ZERO, BlockPos.ZERO);
    }

    public static void encode(CaptureBoxPacket message, PacketBuffer buffer) {
        buffer.writeBoolean(message.visible);
        buffer.writeBlockPos(message.first);
        buffer.writeBlockPos(message.second);
    }

    public static CaptureBoxPacket decode(PacketBuffer buffer) {
        return new CaptureBoxPacket(buffer.readBoolean(), buffer.readBlockPos(),
                buffer.readBlockPos());
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            if (this.visible) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientCaptureBox.set(this.first, this.second));
            } else {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientCaptureBox::clear);
            }
        });
        context.setPacketHandled(true);
    }
}
