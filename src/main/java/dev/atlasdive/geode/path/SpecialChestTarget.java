package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.List;

/** Matching and approach geometry for the elevated Sewer special chest. */
public final class SpecialChestTarget {
    private static final int X = 99;
    private static final int Z = 46;
    private static final int MIN_Y = 61;
    private static final int MAX_Y = 63;

    private SpecialChestTarget() {}

    /** Accept the special chest at any of the configured vertical block positions. */
    public static boolean matches(BlockPos chest) {
        return chest != null && chest.x == X && chest.z == Z
                && chest.y >= MIN_Y && chest.y <= MAX_Y;
    }

    /** Keep the same three-block vertical offset used by the original Y=61 approach. */
    public static double approachFeetY(BlockPos chest) {
        if (!matches(chest)) throw new IllegalArgumentException("not a special chest target");
        return chest.y - 3.0D;
    }

    /**
     * The X coordinate of the precision target lies exactly on a block boundary.
     * Do not let the nearest-lane mapping select a single possibly blocked edge lane:
     * expose every collision-tested lane in that same approach cell as a valid goal.
     */
    public static List<PathNode> approachGoals(BlockPos chest) {
        double feetY=approachFeetY(chest);
        int baseX=99,baseZ=47;
        List<PathNode> goals=new ArrayList<PathNode>(40);
        for(int dx=-1;dx<=1;dx++){
            for(int dz=-1;dz<=1;dz++){
                int x=baseX+dx,z=baseZ+dz;
                // Never choose the chest's own occupied block as a standing position.
                if(x==chest.x&&z==chest.z)continue;
                PathNode anchor=PathNode.atWorld(x+0.5D,feetY,z+0.5D);
                for(int lane=0;lane<PathNode.LANE_COUNT;lane++)goals.add(anchor.withLane(lane));
            }
        }
        return goals;
    }
}
