package dev.atlasdive.geode.path;

/**
 * Resolves a pathfinding seed to the nearest collision-tested standing node.
 * This handles players who start on narrow/partial blocks or slightly inside
 * pane-like geometry where their nearest lane is not itself a valid node.
 */
public final class PathStartResolver {
    private static final int HORIZONTAL_RADIUS=2;
    private static final int HALF_HEIGHT_RADIUS=2;
    private static final double MAX_HORIZONTAL_DISTANCE_SQUARED=2.25D;
    private static final double MAX_VERTICAL_DISTANCE=1.0D;

    private PathStartResolver() {}

    public static PathNode resolve(PathWorld world,double worldX,double feetY,double worldZ) {
        if(world==null)return null;
        PathNode requested=PathNode.atPlayer(worldX,feetY,worldZ);
        PathNode best=null;
        double bestScore=Double.POSITIVE_INFINITY;
        for(int dx=-HORIZONTAL_RADIUS;dx<=HORIZONTAL_RADIUS;dx++){
            for(int dz=-HORIZONTAL_RADIUS;dz<=HORIZONTAL_RADIUS;dz++){
                for(int dy2=-HALF_HEIGHT_RADIUS;dy2<=HALF_HEIGHT_RADIUS;dy2++){
                    int y2=requested.y2+dy2;
                    for(int lane=0;lane<PathNode.LANE_COUNT;lane++){
                        PathNode candidate=new PathNode(requested.x+dx,y2,requested.z+dz,lane);
                        if(!world.canStand(candidate))continue;
                        double x=candidate.worldX()-worldX;
                        double z=candidate.worldZ()-worldZ;
                        double horizontalSquared=x*x+z*z;
                        double vertical=Math.abs(candidate.feetY()-feetY);
                        if(horizontalSquared>MAX_HORIZONTAL_DISTANCE_SQUARED
                                ||vertical>MAX_VERTICAL_DISTANCE)continue;
                        // Staying on the same level is preferable to inventing a jump
                        // merely to get a valid seed. Slightly favour the centre on ties.
                        double score=horizontalSquared+4.0D*vertical*vertical
                                +(lane==PathNode.CENTER?0.0D:0.005D);
                        if(score<bestScore){
                            bestScore=score;
                            best=candidate;
                        }
                    }
                }
            }
        }
        return best;
    }
}
