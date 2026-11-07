package dev.atlasdive.geode.path;

/** Splits long horizontal chest routes into bounded A* segments. */
public final class ChestRoutePlanner {
    private ChestRoutePlanner() {}

    /**
     * Returns the next transit waypoint, or null if the chest is already within
     * maxSegment horizontal blocks and its final approach candidates can be used.
     */
    public static BlockPos nextWaypoint(double startX,double startZ,BlockPos chest,
                                        int maxSegment,int y) {
        if(chest==null)throw new IllegalArgumentException("chest cannot be null");
        if(maxSegment<1)throw new IllegalArgumentException("maxSegment must be positive");
        double dx=chest.x+0.5D-startX;
        double dz=chest.z+0.5D-startZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        if(distance<=maxSegment)return null;
        double t=maxSegment/distance;
        return new BlockPos((int)Math.floor(startX+dx*t),y,(int)Math.floor(startZ+dz*t));
    }
}
