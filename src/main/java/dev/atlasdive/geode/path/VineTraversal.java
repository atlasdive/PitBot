package dev.atlasdive.geode.path;

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
