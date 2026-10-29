package com.tunwell.buildcapture.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PreviewPackingTest {
    @Test
    void roundTripsCoordinates() {
        for (int x : new int[]{0, 1, 512, PreviewPacking.AXIS_MASK}) {
            for (int y : new int[]{0, 3, PreviewPacking.AXIS_MASK}) {
                for (int z : new int[]{0, 7, 1000}) {
                    int packed = PreviewPacking.pack(x, y, z);
                    assertEquals(x, PreviewPacking.x(packed));
                    assertEquals(y, PreviewPacking.y(packed));
                    assertEquals(z, PreviewPacking.z(packed));
                }
            }
        }
    }

    @Test
    void axesDoNotOverlap() {
        assertNotEquals(PreviewPacking.pack(1, 0, 0), PreviewPacking.pack(0, 1, 0));
        assertNotEquals(PreviewPacking.pack(0, 1, 0), PreviewPacking.pack(0, 0, 1));
        assertNotEquals(PreviewPacking.pack(1, 0, 0), PreviewPacking.pack(0, 0, 1));
    }

    @Test
    void axisLimitMatchesBitLayout() {
        assertEquals(1024, PreviewPacking.MAX_AXIS);
        assertEquals(PreviewPacking.MAX_AXIS - 1, PreviewPacking.AXIS_MASK);
    }
}
