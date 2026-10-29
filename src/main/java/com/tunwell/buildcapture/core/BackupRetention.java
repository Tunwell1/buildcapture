package com.tunwell.buildcapture.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Retention rule for timestamped backup files: keep the newest N names and
 * report the oldest ones as stale. Backup names are fixed-width timestamps, so
 * sorting by file name is the same as sorting by time.
 */
public final class BackupRetention {
    private BackupRetention() {
    }

    /** Files to delete, oldest first; empty when there is nothing to prune. */
    public static List<Path> stale(List<Path> files, int keep) {
        if (files.size() <= keep) {
            return Collections.emptyList();
        }
        List<Path> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return new ArrayList<>(sorted.subList(0, sorted.size() - keep));
    }
}
