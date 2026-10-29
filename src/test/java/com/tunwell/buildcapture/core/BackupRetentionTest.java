package com.tunwell.buildcapture.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackupRetentionTest {
    private static Path backup(int index) {
        return Paths.get("backups", String.format("20260101-%06d-000.nbt", index));
    }

    @Test
    void prunesTheOldestBeyondTheLimit() {
        List<Path> files = new ArrayList<>();
        for (int index = 0; index < 23; index++) {
            files.add(backup(index));
        }
        List<Path> stale = BackupRetention.stale(files, 20);
        assertEquals(3, stale.size());
        assertEquals(backup(0), stale.get(0));
        assertEquals(backup(1), stale.get(1));
        assertEquals(backup(2), stale.get(2));
    }

    @Test
    void keepsEverythingAtOrBelowTheLimit() {
        assertTrue(BackupRetention.stale(new ArrayList<>(), 20).isEmpty());
        List<Path> files = new ArrayList<>();
        files.add(backup(1));
        assertTrue(BackupRetention.stale(files, 20).isEmpty());
    }
}
