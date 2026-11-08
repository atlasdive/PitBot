package dev.atlasdive.geode.path;

import java.util.List;

/** Direction-aware lane and facing helpers for climbing vines. */
public final class VineTraversal {
    private VineTraversal() {}

    public static int laneMask(boolean north,boolean east,boolean south,boolean west){
        int mask=0;
        // Stand on the free side opposite the wall that the vine is attached to.
        if(north)mask|=1<<PathNode.SOUTH;
        if(east)mask|=1<<PathNode.WEST;
        if(south)mask|=1<<PathNode.NORTH;
        if(west)mask|=1<<PathNode.EAST;
        return mask==0?1<<PathNode.CENTER:mask;
    }

    /**
     * Determines the vertical direction toward the nearest route goal.
     * Zero means no vertical climb should be inserted at this vine cell.
     */
    public static int verticalDirection(PathNode current,List<PathNode> goals){
        if(current==null||goals==null||goals.isEmpty())return 0;
        PathNode nearest=null;
        double nearestScore=Double.POSITIVE_INFINITY;
        for(PathNode goal:goals){
            double dx=goal.worldX()-current.worldX();
            double dz=goal.worldZ()-current.worldZ();
            double dy=goal.feetY()-current.feetY();
            double score=dx*dx+dz*dz+dy*dy;
            if(score<nearestScore){nearestScore=score;nearest=goal;}
        }
        if(nearest==null)return 0;
        double dy=nearest.feetY()-current.feetY();
        return dy>0.25D?1:dy< -0.25D?-1:0;
    }

    public static float yawForLane(int lane,float fallback){
        switch(lane){
            case PathNode.WEST:return -90.0F;
            case PathNode.EAST:return 90.0F;
            case PathNode.NORTH:return 0.0F;
            case PathNode.SOUTH:return -180.0F;
            default:return fallback;
        }
    }
}
