package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A known transition from the upper Sewer approach to its interior landing point. */
public final class SewerEntrance {
    private static final List<SewerEntrance> ENTRANCES;

    static {
        List<SewerEntrance> entries = new ArrayList<SewerEntrance>();
        entries.add(new SewerEntrance("water-entry-1",
                new BlockPos(116, 68, 28), new BlockPos(112, 59, 30), true));
        entries.add(new SewerEntrance("entry-2",
                new BlockPos(106, 66, 54), new BlockPos(94, 58, 56), false));
        ENTRANCES = Collections.unmodifiableList(entries);
    }

    private final String id;
    private final BlockPos start;
    private final BlockPos destination;
    private final boolean passesThroughWater;

    private SewerEntrance(String id, BlockPos start, BlockPos destination, boolean passesThroughWater) {
        this.id = id;
        this.start = start;
        this.destination = destination;
        this.passesThroughWater = passesThroughWater;
    }

    public static List<SewerEntrance> all() {
        return ENTRANCES;
    }

    /** Finds the route selected by A* from the block containing its upper endpoint. */
    public static SewerEntrance byStart(BlockPos start) {
        if (start == null) return null;
        for (SewerEntrance entrance : ENTRANCES) {
            if (entrance.start.equals(start)) return entrance;
        }
        return null;
    }

    public String getId() { return id; }
    public BlockPos getStart() { return start; }
    public BlockPos getDestination() { return destination; }
    public boolean passesThroughWater() { return passesThroughWater; }
}
