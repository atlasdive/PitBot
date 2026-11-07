package dev.atlasdive.geode.path;

/** Generates a non-repeating square-spiral route for incrementally exploring loaded chunks. */
public final class PatrolPlanner {
    private PatrolPlanner() {}

    /**
     * Returns the waypoint for a zero-based patrol leg.
     * Leg lengths grow every two turns: 8, 8, 12, 12, 16, 16, ...
     */
    public static BlockPos waypoint(BlockPos origin,int leg,int y) {
        if(origin==null)throw new IllegalArgumentException("origin cannot be null");
        if(leg<0)throw new IllegalArgumentException("leg must be non-negative");
        int x=origin.x,z=origin.z;
        for(int i=0;i<=leg;i++){
            int distance=8+(i/2)*4;
            switch(i&3){
                case 0: z-=distance; break; // north
                case 1: x+=distance; break; // east
                case 2: z+=distance; break; // south
                default: x-=distance; break; // west
            }
        }
        return new BlockPos(x,y,z);
    }

    public static boolean insideHorizontalRadius(BlockPos point,BlockPos center,int radius) {
        if(point==null||center==null||radius<0)return false;
        long dx=(long)point.x-center.x,dz=(long)point.z-center.z;
        return dx*dx+dz*dz<=(long)radius*radius;
    }
}
