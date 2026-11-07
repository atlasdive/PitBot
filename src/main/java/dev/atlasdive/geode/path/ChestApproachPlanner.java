package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.List;

/** Generates safe standing-position candidates around a chest at several elevations. */
public final class ChestApproachPlanner {
    private ChestApproachPlanner() {}

    public static List<BlockPos> candidates(BlockPos chest,int horizontalRadius,int verticalRange) {
        if(chest==null)throw new IllegalArgumentException("chest cannot be null");
        if(horizontalRadius<1||verticalRange<0)throw new IllegalArgumentException("invalid approach range");
        List<BlockPos> positions=new ArrayList<BlockPos>();
        int radiusSquared=horizontalRadius*horizontalRadius;
        for(int dx=-horizontalRadius;dx<=horizontalRadius;dx++){
            for(int dz=-horizontalRadius;dz<=horizontalRadius;dz++){
                if(dx==0&&dz==0)continue;
                if(dx*dx+dz*dz>radiusSquared)continue;
                for(int dy=-verticalRange;dy<=verticalRange;dy++){
                    positions.add(chest.add(dx,dy,dz));
                }
            }
        }
        return positions;
    }
}
