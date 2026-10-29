package com.tunwell.buildcapture.core;

import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Confines a resolved file to a root directory. Both sides are made absolute and
 * normalized first: a relative root (a dev server started with {@code --gameDir .})
 * still contains a {@code .} element, and normalizing only the file made a
 * legitimate path look like it escaped.
 */
public final class PathSecurity {
    private PathSecurity() {
    }

    /** The normalized absolute file, or {@code null} when it leaves the root. */
    @Nullable
    public static Path inside(Path root, Path file) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        return normalizedFile.startsWith(normalizedRoot) ? normalizedFile : null;
    }
}
