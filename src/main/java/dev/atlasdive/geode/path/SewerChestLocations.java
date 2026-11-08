package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Known block locations where Sewer chests can spawn. */
public final class SewerChestLocations {
    private static final double[][] REGISTERED = {
            {53.0D,55.0D,33.0D},
            {80.0D,60.0D,66.0D},
            {40.0D,61.0D,46.0D},
            {89.7D,58.0D,13.4D},
            {47.5D,53.0D,85.7D},
            {47.5D,51.0D,81.6D},
            {106.7D,60.0D,14.7D},
            {78.6D,52.0D,75.4D},
            {69.7D,58.0D,15.7D},
            {82.3D,58.0D,41.3D},
            {67.3D,53.0D,83.3D}
    };

    private SewerChestLocations() {}

    /** Coordinates are floored because each input identifies a Minecraft block cell. */
    public static List<BlockPos> registered() {
        List<BlockPos> result = new ArrayList<BlockPos>(REGISTERED.length);
        for (double[] point : REGISTERED) {
            result.add(new BlockPos(floor(point[0]), floor(point[1]), floor(point[2])));
        }
        return result;
    }

    /**
     * Includes existing special targets in the global map bounds. LinkedHashSet keeps
     * ordering stable while removing duplicates such as the registered scaffold chest.
     */
    public static List<BlockPos> allTargets() {
        Set<BlockPos> unique = new LinkedHashSet<BlockPos>(registered());
        for (int y = 61; y <= 63; y++) unique.add(new BlockPos(99, y, 46));
        return new ArrayList<BlockPos>(unique);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
