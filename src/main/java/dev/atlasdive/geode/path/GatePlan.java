package dev.atlasdive.geode.path;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A detected iron-bar aperture and safe landing column below it. */
public final class GatePlan {
    public final int planeY;
    public final List<BlockPos> landings;
    public final Set<BlockPos> columns;

    GatePlan(int planeY,List<BlockPos> landings,Set<BlockPos> columns){
        this.planeY=planeY;
        this.landings=Collections.unmodifiableList(landings);
        this.columns=Collections.unmodifiableSet(new HashSet<BlockPos>(columns));
    }
}
