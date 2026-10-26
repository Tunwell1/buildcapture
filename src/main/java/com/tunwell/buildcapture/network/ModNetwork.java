package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL = "3";

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
        CHANNEL.registerMessage(
                2,
                StructureListPacket.class,
                StructureListPacket::encode,
                StructureListPacket::decode,
                StructureListPacket::handle
        );
        CHANNEL.registerMessage(
                3,
                StructureActionPacket.class,
                StructureActionPacket::encode,
                StructureActionPacket::decode,
                StructureActionPacket::handle
        );
        CHANNEL.registerMessage(
                4,
                ImportStartPacket.class,
                ImportStartPacket::encode,
                ImportStartPacket::decode,
                ImportStartPacket::handle
        );
        CHANNEL.registerMessage(
                5,
                ImportChunkPacket.class,
                ImportChunkPacket::encode,
                ImportChunkPacket::decode,
                ImportChunkPacket::handle
        );
        CHANNEL.registerMessage(
                6,
                ImportEndPacket.class,
                ImportEndPacket::encode,
                ImportEndPacket::decode,
                ImportEndPacket::handle
        );
        CHANNEL.registerMessage(
                7,
                OpenGuiPacket.class,
                OpenGuiPacket::encode,
                OpenGuiPacket::decode,
                OpenGuiPacket::handle
        );
    }
}
