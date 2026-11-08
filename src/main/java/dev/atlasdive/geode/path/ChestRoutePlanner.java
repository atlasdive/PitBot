package dev.atlasdive.geode.path;

/** Splits long horizontal chest routes into bounded A* segments. */
public final class ChestRoutePlanner {
    private ChestRoutePlanner() {}

    /**
     * Returns the next transit waypoint, or null if the target is already within
     * maxSegment horizontal blocks and its final approach candidates can be used.
     */
    public static BlockPos nextWaypoint(double startX,double startZ,BlockPos target,
                                        int maxSegment,int y) {
        if(target==null)throw new IllegalArgumentException("target cannot be null");
        if(maxSegment<1)throw new IllegalArgumentException("maxSegment must be positive");
        double dx=target.x+0.5D-startX;
        double dz=target.z+0.5D-startZ;
        double distance=Math.sqrt(dx*dx+dz*dz);
        if(distance<=maxSegment)return null;
        double t=maxSegment/distance;
        return new BlockPos((int)Math.floor(startX+dx*t),y,(int)Math.floor(startZ+dz*t));
    }

    /** Reject transit goals that do not make measurable progress toward the target. */
    public static boolean makesProgress(double startX,double startZ,
            double goalX,double goalZ,BlockPos target,double minimumProgress) {
        if(target==null||minimumProgress<0.0D)return false;
        double before=horizontalDistance(startX,startZ,target.x+0.5D,target.z+0.5D);
        double after=horizontalDistance(goalX,goalZ,target.x+0.5D,target.z+0.5D);
        return before-after>=minimumProgress;
    }

    public static double horizontalDistance(double x1,double z1,double x2,double z2) {
        double dx=x2-x1,dz=z2-z1;
        return Math.sqrt(dx*dx+dz*dz);
    }
}
