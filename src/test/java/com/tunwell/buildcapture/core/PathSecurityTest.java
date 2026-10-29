package com.tunwell.buildcapture.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PathSecurityTest {
    @Test
    void acceptsAFileUnderARelativeRootWithDotElements() {
        // A dev server started with --gameDir . produces a root like .\run\world.
        Path root = Paths.get(".").resolve("run").resolve("world").resolve("datapacks");
        Path file = root.resolve("data").resolve("buildcapture")
                .resolve("structures").resolve("test.nbt");
        Path resolved = PathSecurity.inside(root, file);
        assertNotNull(resolved);
        assertEquals(file.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void rejectsEscapesAfterNormalization() {
        Path root = Paths.get(".").resolve("run").resolve("world").resolve("datapacks");
        assertNull(PathSecurity.inside(root, root.resolve("..").resolve("..").resolve("evil.nbt")));
        assertNull(PathSecurity.inside(root, root.resolve("data").resolve("..").resolve("..")
                .resolve("..").resolve("evil.nbt")));
    }

    @Test
    void worksWithAbsoluteRoots() {
        Path root = Paths.get("build").toAbsolutePath().normalize();
        assertNotNull(PathSecurity.inside(root, root.resolve("test.nbt")));
        assertNull(PathSecurity.inside(root, root.resolve("..").resolve("test.nbt")));
    }
}
