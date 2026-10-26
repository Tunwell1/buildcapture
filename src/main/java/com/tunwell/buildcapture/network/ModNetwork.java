package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "1";

    // The capture commands run on the server, so the channel must also accept
    // vanilla clients: they cannot render the box, but they can play and use
    // the commands.
    private static boolean compatibleVersion(String version) {
        return PROTOCOL.equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BuildCapture.MOD_ID, "main"),
            () -> PROTOCOL,
            ModNetwork::compatibleVersion,
            ModNetwork::compatibleVersion
    );

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(
                0,
                CaptureBoxPacket.class,
                CaptureBoxPacket::encode,
                CaptureBoxPacket::decode,
                CaptureBoxPacket::handle
        );
        CHANNEL.registerMessage(
                1,
                CaptureBoundsPacket.class,
                CaptureBoundsPacket::encode,
                CaptureBoundsPacket::decode,
                CaptureBoundsPacket::handle
        );
    }
}
