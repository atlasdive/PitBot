package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class PathfinderTest {
    @Test public void findsFlatFloorRoute(){
        TestWorld world=new TestWorld();
        PathResult result=new Pathfinder().find(world,new BlockPos(0,1,0),
                java.util.Collections.singletonList(new BlockPos(5,1,4)),10000,8);
        assertTrue(result.found());
        assertEquals(new BlockPos(5,1,4),result.steps.get(result.steps.size()-1).pos);
    }

    @Test public void clearDropHasNoFallDamageSurchargeAndIsNotHiddenByNearbyLedge(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(0,5,0));
        // A nearer ledge exists at y=4, but A* must also consider the lower landing at y=0.
        world.support.add(new BlockPos(1,4,0));
        world.support.add(new BlockPos(1,0,0));
        PathResult result=new Pathfinder().find(world,new BlockPos(0,5,0),
                java.util.Collections.singletonList(new BlockPos(1,0,0)),1000,8);
        assertTrue(result.found());
        assertEquals(2,result.steps.size());
        assertEquals(MovementKind.DROP,result.steps.get(1).kind);
        assertEquals(new BlockPos(1,0,0),result.steps.get(1).pos);
        assertTrue("drop cost should only be horizontal distance",result.steps.get(1).cost<1.1D);
    }

    private static final class TestWorld implements PathWorld {
        final Set<BlockPos> support=new HashSet<BlockPos>();
        TestWorld(){for(int x=-2;x<=10;x++)for(int z=-2;z<=10;z++)support.add(new BlockPos(x,1,z));}
        public boolean canStand(BlockPos p){return p.y>=0&&p.y<8&&support.contains(p);}
        public boolean clear(BlockPos p){return p.y>=0&&p.y<8;}
        public boolean hazard(BlockPos p){return false;}
        public boolean climbable(BlockPos p){return false;}
        public boolean liquid(BlockPos p){return false;}
        public double extraCost(BlockPos p){return 0D;}
        public boolean jumpArcClear(BlockPos a,BlockPos b){return true;}
    }
}
