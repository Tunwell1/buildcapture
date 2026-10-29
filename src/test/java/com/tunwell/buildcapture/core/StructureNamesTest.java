package com.tunwell.buildcapture.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureNamesTest {
    @Test
    void acceptsOrdinaryNestedIds() {
        assertTrue(StructureNames.isSafe("buildcapture", "castle/tower"));
        assertTrue(StructureNames.isSafe("buildcapture", "a.b-c_d"));
    }

    @Test
    void rejectsTraversalAndEmptySegments() {
        assertFalse(StructureNames.isSafe("buildcapture", ".."));
        assertFalse(StructureNames.isSafe("buildcapture", "../x"));
        assertFalse(StructureNames.isSafe("buildcapture", "a/../b"));
        assertFalse(StructureNames.isSafe("buildcapture", "a//b"));
        assertFalse(StructureNames.isSafe("buildcapture", "a/./b"));
        assertFalse(StructureNames.isSafe("buildcapture", ""));
        assertFalse(StructureNames.isSafe("..", "x"));
        assertFalse(StructureNames.isSafe("", "x"));
    }

    @Test
    void sanitizerDropsTraversalAndKeepsFolders() {
        assertEquals("evil_build", StructureNames.sanitizeFileName("../../Evil Build.nbt"));
        assertEquals("castle/tower", StructureNames.sanitizeFileName("Castle/Tower.nbt"));
        assertEquals("", StructureNames.sanitizeFileName(".."));
        assertEquals("", StructureNames.sanitizeFileName(null));
    }

    @Test
    void sanitizerLimitsLength() {
        String longName = new String(new char[200]).replace('\0', 'a');
        assertTrue(StructureNames.sanitizeFileName(longName).length()
                <= StructureNames.MAX_FILE_NAME);
    }

    @Test
    void mapsRelativePathsBackToIds() {
        assertEquals("buildcapture:castle/tower",
                StructureNames.idFromRelative("buildcapture/structures/castle/tower.nbt"));
        assertNull(StructureNames.idFromRelative("buildcapture/other/x.nbt"));
        assertNull(StructureNames.idFromRelative("no-slash.nbt"));
        assertNull(StructureNames.idFromRelative(null));
    }
}
