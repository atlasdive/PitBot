package dev.atlasdive.geode.path;

/** Generates a non-repeating square spiral for unbounded chunk exploration. */
public final class PatrolPlanner {
    private static final long WORLD_LIMIT=29999900L;

    private PatrolPlanner() {}

    /**
     * Returns the waypoint for a zero-based patrol leg.
     * Leg lengths grow every two turns: 8, 8, 16, 16, 24, 24, ...
     * The arithmetic-series calculation is O(1), even for very distant legs.
     */
    public static BlockPos waypoint(BlockPos origin,int leg,int y) {
        if(origin==null)throw new IllegalArgumentException("origin cannot be null");
        if(leg<0)throw new IllegalArgumentException("leg must be non-negative");

        long north=leg/4L+1L;
        long east=leg>=1?(leg-1L)/4L+1L:0L;
        long south=leg>=2?(leg-2L)/4L+1L:0L;
        long west=leg>=3?(leg-3L)/4L+1L:0L;

        long x=seriesSum(8L,16L,east)-seriesSum(16L,16L,west);
        long z=seriesSum(16L,16L,south)-seriesSum(8L,16L,north);
        return new BlockPos(toWorldCoordinate(origin.x+x),y,toWorldCoordinate(origin.z+z));
    }

    private static long seriesSum(long first,long step,long count) {
        return count*first+step*count*(count-1L)/2L;
    }

    private static int toWorldCoordinate(long value) {
        return (int)Math.max(-WORLD_LIMIT,Math.min(WORLD_LIMIT,value));
    }
}
