package com.tunwell.buildcapture.network;

import com.tunwell.buildcapture.BuildCapture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;

import java.util.function.Predicate;

public final class ModNetwork {
    private static final String PROTOCOL = "7";

    // The capture commands run on the server, so the channel must also accept
    // clients that do not have the mod: they cannot render the box, but they can
    // play and use the commands. Forge reports a missing channel as ABSENT and
    // a client without any Forge mods as ACCEPTVANILLA; acceptMissingOr covers
    // both, which a bare ACCEPTVANILLA check does not.
    private static final Predicate<String> ACCEPTED_VERSIONS =
            NetworkRegistry.acceptMissingOr(PROTOCOL);

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BuildCapture.MOD_ID, "main"),
            () -> PROTOCOL,
            ACCEPTED_VERSIONS,
            ACCEPTED_VERSIONS
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
        CHANNEL.registerMessage(
                8,
                PreviewRequestPacket.class,
                PreviewRequestPacket::encode,
                PreviewRequestPacket::decode,
                PreviewRequestPacket::handle
        );
        CHANNEL.registerMessage(
                9,
                PreviewDataPacket.class,
                PreviewDataPacket::encode,
                PreviewDataPacket::decode,
                PreviewDataPacket::handle
        );
        CHANNEL.registerMessage(
                10,
                CaptureCommandPacket.class,
                CaptureCommandPacket::encode,
                CaptureCommandPacket::decode,
                CaptureCommandPacket::handle
        );
    }
}
