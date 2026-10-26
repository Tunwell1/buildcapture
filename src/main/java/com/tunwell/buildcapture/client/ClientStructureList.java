package com.tunwell.buildcapture.client;

import com.tunwell.buildcapture.capture.StructureCatalog;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.List;

/**
 * The structure list last received from the server. Kept outside the screen so
 * a reply that arrives while the screen is closing is not lost, and so the
 * screen can be reopened without another round trip.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientStructureList {
    private static List<StructureCatalog.Entry> entries = Collections.emptyList();
    private static String libraryPath = "";
    private static boolean canUndo;
    private static int version;

    private ClientStructureList() {
    }

    public static void set(List<StructureCatalog.Entry> list, String path, boolean undo) {
        entries = Collections.unmodifiableList(list);
        libraryPath = path == null ? "" : path;
        canUndo = undo;
        version++;
    }

    public static List<StructureCatalog.Entry> entries() {
        return entries;
    }

    public static String libraryPath() {
        return libraryPath;
    }

    /** True when the server has a capture of this player that can be reverted. */
    public static boolean canUndo() {
        return canUndo;
    }

    /** Bumped on every server reply, so the screen knows when to rebuild. */
    public static int version() {
        return version;
    }
}
