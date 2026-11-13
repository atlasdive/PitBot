package dev.atlasdive.geode.path;

import org.junit.Test;
import static org.junit.Assert.*;

public class RequiredCorridorPathWorldTest {
    @Test public void onlyTheConfiguredPassageCellsAreStandableInsideTheBand(){
        RequiredCorridorPathWorld world=new RequiredCorridorPathWorld(new OpenWorld());
        assertFalse(world.canStand(new PathNode(93,114,32)));
        assertFalse(world.canStand(new PathNode(93,117,32)));
        assertTrue(world.canStand(new PathNode(93,116,32)));
        assertTrue(world.canStand(new PathNode(93,116,35)));
        assertTrue(world.canStand(new PathNode(92,116,32)));
        assertTrue(world.canStand(new PathNode(94,116,32)));
    }

    @Test public void cannotJumpOrSmoothAcrossThePassageColumn(){
        RequiredCorridorPathWorld world=new RequiredCorridorPathWorld(new OpenWorld());
        PathNode left=new PathNode(92,116,32);
        PathNode portal=new PathNode(93,116,32);
        PathNode right=new PathNode(94,116,32);
        assertFalse(world.transitionClear(left,right));
        assertFalse(world.jumpArcClear(left,right));
        assertFalse(world.lineClear(left,right));
        assertTrue(world.transitionClear(left,portal));
        assertTrue(world.transitionClear(portal,right));
    }

    @Test public void startResolverMovesAStartOutOfTheForbiddenPartOfThePassage(){
        RequiredCorridorPathWorld world=new RequiredCorridorPathWorld(new OpenWorld());
        PathNode resolved=PathStartResolver.resolve(world,93.5D,57.0D,32.5D);
        assertNotNull(resolved);
        assertTrue(world.canStand(resolved));
        assertFalse("the unsafe original position must not be reused",
                resolved.equals(new PathNode(93,114,32)));
    }

    private static final class OpenWorld implements PathWorld {
        public boolean canStand(BlockPos pos){return true;}
        public boolean clear(BlockPos pos){return true;}
        public boolean hazard(BlockPos pos){return false;}
        public boolean climbable(BlockPos pos){return false;}
        public boolean liquid(BlockPos pos){return false;}
        public boolean gateBarrier(BlockPos pos){return false;}
        public double extraCost(BlockPos pos){return 0.0D;}
        public boolean jumpArcClear(BlockPos from,BlockPos to){return true;}
        public boolean lineClear(BlockPos from,BlockPos to){return true;}
        public boolean canStand(PathNode pos){return true;}
        public boolean clear(PathNode pos){return true;}
        public boolean transitionClear(PathNode from,PathNode to){return true;}
        public boolean lineClear(PathNode from,PathNode to){return true;}
        public boolean jumpArcClear(PathNode from,PathNode to){return true;}
    }
}
