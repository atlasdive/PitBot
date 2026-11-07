package dev.atlasdive.geode.path;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A passable iron-bar aperture plus safe standing approach cells above it. */
public final class GatePlan {
    public final int planeY;
    public final List<BlockPos> approaches;
    public final Map<BlockPos,BlockPos> approachToAperture;
    public final Set<BlockPos> columns;

    GatePlan(int planeY,List<BlockPos> approaches,Map<BlockPos,BlockPos> approachToAperture,
             Set<BlockPos> columns){
        this.planeY=planeY;
        this.approaches=Collections.unmodifiableList(approaches);
        this.approachToAperture=Collections.unmodifiableMap(new HashMap<BlockPos,BlockPos>(approachToAperture));
        this.columns=Collections.unmodifiableSet(new HashSet<BlockPos>(columns));
    }
}
