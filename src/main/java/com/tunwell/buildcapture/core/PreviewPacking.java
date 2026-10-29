package com.tunwell.buildcapture.core;

/**
 * Bit layout of the placement ghost's block positions. Positions travel in a
 * single packet as 10 bits per axis, which is why the server only ghosts
 * structures up to {@link #MAX_AXIS} blocks per axis.
 */
public final class PreviewPacking {
    public static final int AXIS_BITS = 10;
    public static final int AXIS_MASK = (1 << AXIS_BITS) - 1;
    public static final int MAX_AXIS = 1 << AXIS_BITS;

    private PreviewPacking() {
    }

    public static int pack(int x, int y, int z) {
        return (x & AXIS_MASK)
                | ((y & AXIS_MASK) << AXIS_BITS)
                | ((z & AXIS_MASK) << (AXIS_BITS * 2));
    }

    public static int x(int packed) {
        return packed & AXIS_MASK;
    }

    public static int y(int packed) {
        return (packed >> AXIS_BITS) & AXIS_MASK;
    }

    public static int z(int packed) {
        return (packed >> (AXIS_BITS * 2)) & AXIS_MASK;
    }
}
