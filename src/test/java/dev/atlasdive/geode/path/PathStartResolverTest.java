package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class PathStartResolverTest {
    @Test
    public void resolvesCarpetHeightToItsHalfBlockStandingNode() {
        StandWorld world=new StandWorld();
        PathNode carpetTop=new PathNode(3,128,4,PathNode.CENTER);
        world.add(carpetTop);

        PathNode resolved=PathStartResolver.resolve(world,3.5D,64.0625D,4.5D);

        assertEquals("a 1/16-block carpet should map to the node directly beneath its top",
                carpetTop,resolved);
    }

    @Test
    public void usesSupportedCentreWhenThePlayersNearestStairLaneIsUnsupported() {
        StandWorld world=new StandWorld();
        PathNode centre=new PathNode(0,129,0,PathNode.CENTER);
        world.add(centre);

        PathNode resolved=PathStartResolver.resolve(world,0.15D,64.5D,0.5D);

        assertEquals("the resolver must not fail just because the nearest lane is blocked",
                centre,resolved);
    }

    @Test
    public void canEscapeFromTheMiddleOfIronBarsToANearbyClearCell() {
        StandWorld world=new StandWorld();
        PathNode outsideBars=new PathNode(1,128,0,PathNode.CENTER);
        world.add(outsideBars);

        PathNode resolved=PathStartResolver.resolve(world,0.5D,64.0D,0.5D);

        assertEquals("a blocked current cell should seed A* from the nearest safe adjacent cell",
                outsideBars,resolved);
    }

    @Test
    public void returnsNullInsteadOfInventingAnUnsupportedStart() {
        StandWorld world=new StandWorld();

        assertNull(PathStartResolver.resolve(world,0.5D,64.0D,0.5D));
    }

    private static final class StandWorld implements PathWorld {
        private final Set<PathNode> standable=new HashSet<PathNode>();
        void add(PathNode node){standable.add(node);}

        @Override public boolean canStand(PathNode p){return standable.contains(p);}
        @Override public boolean clear(PathNode p){return true;}
        @Override public boolean hazard(PathNode p){return false;}
        @Override public double extraCost(PathNode p){return 0.0D;}
        @Override public boolean climbable(PathNode p){return false;}
        @Override public boolean liquid(PathNode p){return false;}
        @Override public boolean jumpArcClear(PathNode from,PathNode to){return true;}
        @Override public boolean lineClear(PathNode from,PathNode to){return from.y2==to.y2;}
        @Override public boolean canStand(BlockPos p){return false;}
        @Override public boolean clear(BlockPos p){return true;}
        @Override public boolean hazard(BlockPos p){return false;}
        @Override public boolean climbable(BlockPos p){return false;}
        @Override public boolean liquid(BlockPos p){return false;}
        @Override public boolean gateBarrier(BlockPos p){return false;}
        @Override public double extraCost(BlockPos p){return 0.0D;}
        @Override public boolean jumpArcClear(BlockPos from,BlockPos to){return true;}
        @Override public boolean lineClear(BlockPos from,BlockPos to){return from.y==to.y;}
    }
}
