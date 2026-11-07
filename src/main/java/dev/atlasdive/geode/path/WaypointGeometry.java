package dev.atlasdive.geode.path;

/** Pure geometry for waypoint pass detection and sprint-safe cornering. */
public final class WaypointGeometry {
    private WaypointGeometry(){}

    /**
     * Returns true once the player has crossed the target plane along the incoming
     * segment, provided they stayed close enough to that segment.
     */
    public static boolean hasPassedCheckpoint(double previousX,double previousZ,
            double targetX,double targetZ,double playerX,double playerZ,double maxCrossTrack){
        double sx=targetX-previousX,sz=targetZ-previousZ;
        double lengthSquared=sx*sx+sz*sz;
        if(lengthSquared<0.04D)return false;
        double px=playerX-previousX,pz=playerZ-previousZ;
        double along=px*sx+pz*sz;
        if(along<lengthSquared)return false;
        double crossTrack=Math.abs(px*sz-pz*sx)/Math.sqrt(lengthSquared);
        return crossTrack<=maxCrossTrack;
    }

    /** A turn is sharp when the incoming and outgoing segment directions diverge. */
    public static boolean isSharpTurn(double previousX,double previousZ,
            double waypointX,double waypointZ,double nextX,double nextZ,double thresholdDegrees){
        double ax=waypointX-previousX,az=waypointZ-previousZ;
        double bx=nextX-waypointX,bz=nextZ-waypointZ;
        double al=Math.sqrt(ax*ax+az*az),bl=Math.sqrt(bx*bx+bz*bz);
        if(al<0.02D||bl<0.02D)return false;
        double cosine=(ax*bx+az*bz)/(al*bl);
        cosine=Math.max(-1.0D,Math.min(1.0D,cosine));
        return cosine<Math.cos(Math.toRadians(thresholdDegrees));
    }

    /** Sprint on long straight approaches; coast before close checkpoints and corners. */
    public static boolean shouldSprint(double distanceToWaypoint,boolean sharpTurn){
        return distanceToWaypoint>1.35D&&(!sharpTurn||distanceToWaypoint>2.8D);
    }
}
