package dev.atlasdive.geode.path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fixed Sewer checkpoints used to approach the special chest at (99,58,46). */
public final class SpecialChestRoutePlanner {
    private static final BlockPos FINAL_TARGET=new BlockPos(99,58,46);
    private static final BlockPos[] CHECKPOINTS={
            new BlockPos(47,51,70),
            new BlockPos(69,53,72),
            new BlockPos(78,53,58),
            new BlockPos(64,55,59),
            new BlockPos(47,52,55),
            new BlockPos(83,58,55),
            new BlockPos(104,58,50),
            new BlockPos(107,58,39),
            new BlockPos(93,58,32),
            new BlockPos(89,58,37),
            new BlockPos(83,57,24),
            new BlockPos(66,54,17),
            new BlockPos(103,58,12)
    };

    private SpecialChestRoutePlanner(){}

    public static BlockPos finalTarget(){return FINAL_TARGET;}

    /**
     * The final chest routine uses the same horizontal segment limit as the
     * regular chest route. Once inside that range, intermediate checkpoints
     * are no longer necessary.
     */
    public static boolean isWithinFinalApproachRange(double x,double z,int maxSegment){
        return ChestRoutePlanner.nextWaypoint(x,z,FINAL_TARGET,maxSegment,FINAL_TARGET.y)==null;
    }

    public static List<BlockPos> checkpoints(){
        List<BlockPos> result=new ArrayList<BlockPos>(CHECKPOINTS.length);
        Collections.addAll(result,CHECKPOINTS);
        return Collections.unmodifiableList(result);
    }

    public static List<BlockPos> remaining(Set<BlockPos> visited){
        Set<BlockPos> completed=visited==null?Collections.<BlockPos>emptySet():visited;
        List<BlockPos> result=new ArrayList<BlockPos>(CHECKPOINTS.length);
        for(BlockPos point:CHECKPOINTS)if(!completed.contains(point))result.add(point);
        return result;
    }

    /** Maps a path endpoint back to its exact configured checkpoint block. */
    public static BlockPos matchCheckpoint(BlockPos endpoint,Collection<BlockPos> candidates){
        if(endpoint==null||candidates==null)return null;
        for(BlockPos candidate:candidates)if(candidate.equals(endpoint))return candidate;
        return null;
    }

    public static boolean hasDuplicates(){
        return new HashSet<BlockPos>(checkpoints()).size()!=CHECKPOINTS.length;
    }
}
