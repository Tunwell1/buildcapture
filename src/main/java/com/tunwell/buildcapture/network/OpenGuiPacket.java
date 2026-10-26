package com.tunwell.buildcapture.network;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.function.Supplier;

/** Asks a modded client to open the structure browser, used by {@code /capture gui}. */
public final class OpenGuiPacket {
    public static void encode(OpenGuiPacket message, PacketBuffer buffer) {
    }

    public static OpenGuiPacket decode(PacketBuffer buffer) {
        return new OpenGuiPacket();
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> com.tunwell.buildcapture.client.CaptureScreen::open));
        context.setPacketHandled(true);
    }
}
