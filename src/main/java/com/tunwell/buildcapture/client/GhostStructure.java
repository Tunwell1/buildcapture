package com.tunwell.buildcapture.client;

import net.minecraft.block.BlockState;
import net.minecraft.util.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Block list of one structure, kept in its unrotated form and re-packed when
 * the player rotates the preview. The position mapping matches
 * {@link net.minecraft.world.gen.feature.template.Template} placement, so the
 * ghost sits exactly where the server will put the blocks.
 */
@OnlyIn(Dist.CLIENT)
public final class GhostStructure {
    private static final int AXIS_BITS = 10;
    private static final int AXIS_MASK = (1 << AXIS_BITS) - 1;

    private final String id;
    private final int baseSizeX;
    private final int baseSizeY;
    private final int baseSizeZ;
    private final int totalBlocks;
    private final BlockState[] basePalette;
    private final int[] basePositions;
    private final int[] baseIndexes;

    private int rotationSteps;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private BlockState[] palette;
    private int[] positions;
    private boolean[] visible;

    public GhostStructure(String id, int sizeX, int sizeY, int sizeZ, int totalBlocks,
                          BlockState[] palette, int[] positions, int[] indexes) {
        this.id = id;
        this.baseSizeX = Math.max(1, sizeX);
        this.baseSizeY = Math.max(1, sizeY);
        this.baseSizeZ = Math.max(1, sizeZ);
        this.totalBlocks = totalBlocks;
        this.basePalette = palette;
        this.basePositions = positions;
        this.baseIndexes = indexes;
        rebuild();
    }

    public String id() {
        return id;
    }

    /** Number of blocks that were sent, which may be less than the full structure. */
    public int blockCount() {
        return positions.length;
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public BlockState state(int index) {
        int paletteIndex = baseIndexes[index];
        if (paletteIndex < 0 || paletteIndex >= palette.length) {
            return net.minecraft.block.Blocks.AIR.defaultBlockState();
        }
        return palette[paletteIndex];
    }

    /** Packed local position after rotation, using {@link #AXIS_BITS} bits per axis. */
    public int position(int index) {
        return positions[index];
    }

    /**
     * False for a block that is completely enclosed by opaque ghost blocks.
     * Skipping those keeps solid builds cheap without changing what is drawn.
     */
    public boolean visible(int index) {
        return visible[index];
    }

    public void rotate(int steps) {
        int normalized = Math.floorMod(steps, 4);
        if (normalized == rotationSteps) {
            return;
        }
        rotationSteps = normalized;
        rebuild();
    }

    private void rebuild() {
        Rotation rotation = rotation(rotationSteps);
        sizeX = rotationSteps % 2 == 0 ? baseSizeX : baseSizeZ;
        sizeY = baseSizeY;
        sizeZ = rotationSteps % 2 == 0 ? baseSizeZ : baseSizeX;

        palette = new BlockState[basePalette.length];
        for (int index = 0; index < basePalette.length; index++) {
            BlockState state = basePalette[index];
            palette[index] = state == null ? net.minecraft.block.Blocks.AIR.defaultBlockState()
                    : state.rotate(rotation);
        }

        positions = new int[basePositions.length];
        for (int index = 0; index < basePositions.length; index++) {
            int packed = basePositions[index];
            int x = packed & AXIS_MASK;
            int y = (packed >> AXIS_BITS) & AXIS_MASK;
            int z = (packed >> (AXIS_BITS * 2)) & AXIS_MASK;
            int rotatedX;
            int rotatedZ;
            switch (rotationSteps) {
                case 1:
                    rotatedX = baseSizeZ - 1 - z;
                    rotatedZ = x;
                    break;
                case 2:
                    rotatedX = baseSizeX - 1 - x;
                    rotatedZ = baseSizeZ - 1 - z;
                    break;
                case 3:
                    rotatedX = z;
                    rotatedZ = baseSizeX - 1 - x;
                    break;
                default:
                    rotatedX = x;
                    rotatedZ = z;
                    break;
            }
            positions[index] = (rotatedX & AXIS_MASK)
                    | ((y & AXIS_MASK) << AXIS_BITS)
                    | ((rotatedZ & AXIS_MASK) << (AXIS_BITS * 2));
        }
        computeVisibility();
    }

    private void computeVisibility() {
        Map<Integer, Integer> stateByPosition = new HashMap<>(positions.length * 2);
        for (int index = 0; index < positions.length; index++) {
            stateByPosition.put(positions[index], baseIndexes[index]);
        }
        visible = new boolean[positions.length];
        for (int index = 0; index < positions.length; index++) {
            int packed = positions[index];
            int stateIndex = baseIndexes[index];
            BlockState state = stateIndex >= 0 && stateIndex < palette.length
                    ? palette[stateIndex] : null;
            if (state == null || !state.canOcclude()) {
                visible[index] = true;
                continue;
            }
            int x = packed & AXIS_MASK;
            int y = (packed >> AXIS_BITS) & AXIS_MASK;
            int z = (packed >> (AXIS_BITS * 2)) & AXIS_MASK;
            visible[index] = !(occludes(stateByPosition, x + 1, y, z)
                    && occludes(stateByPosition, x - 1, y, z)
                    && occludes(stateByPosition, x, y + 1, z)
                    && occludes(stateByPosition, x, y - 1, z)
                    && occludes(stateByPosition, x, y, z + 1)
                    && occludes(stateByPosition, x, y, z - 1));
        }
    }

    private boolean occludes(Map<Integer, Integer> stateByPosition, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
            return false;
        }
        Integer stateIndex = stateByPosition.get((x & AXIS_MASK)
                | ((y & AXIS_MASK) << AXIS_BITS)
                | ((z & AXIS_MASK) << (AXIS_BITS * 2)));
        return stateIndex != null && stateIndex >= 0 && stateIndex < palette.length
                && palette[stateIndex].canOcclude();
    }

    private static Rotation rotation(int steps) {
        switch (Math.floorMod(steps, 4)) {
            case 1:
                return Rotation.CLOCKWISE_90;
            case 2:
                return Rotation.CLOCKWISE_180;
            case 3:
                return Rotation.COUNTERCLOCKWISE_90;
            default:
                return Rotation.NONE;
        }
    }
}
