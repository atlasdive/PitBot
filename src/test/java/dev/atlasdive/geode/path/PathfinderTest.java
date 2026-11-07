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

    @Test public void smoothsStraightWalkingSegments(){
        TestWorld world=new TestWorld();
        PathResult result=new Pathfinder().find(world,new BlockPos(0,1,0),
                java.util.Collections.singletonList(new BlockPos(8,1,0)),10000,8);
        assertTrue(result.found());
        assertTrue("straight path should be reduced to start and end",result.steps.size()<=2);
        assertEquals(new BlockPos(8,1,0),result.steps.get(result.steps.size()-1).pos);
    }

    @Test public void gatePlannerFindsAnOpeningWithoutRequiringLandingBelowIt(){
        TestWorld world=new TestWorld();
        world.support.clear();
        // The player can stand beside the hole, but there is deliberately no
        // supported block directly beneath the hole within the search depth.
        world.support.add(new BlockPos(1,5,0));
        world.support.add(new BlockPos(1,6,0));
        world.iron.add(new BlockPos(-1,4,0));
        world.iron.add(new BlockPos(1,4,0));
        world.iron.add(new BlockPos(0,4,-1));
        world.iron.add(new BlockPos(0,4,1));

        GatePlan gate=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(1,6,0),1,8);
        assertNotNull("opening must be found without a landing directly below it",gate);
        assertEquals(5,gate.planeY);
        assertTrue(gate.approaches.contains(new BlockPos(1,5,0)));
        assertEquals(new BlockPos(0,5,0),gate.approachToAperture.get(new BlockPos(1,5,0)));

        PathResult path=new Pathfinder().find(world,new BlockPos(1,6,0),gate.approaches,1000,8);
        assertTrue("A* should reach the supported approach cell",path.found());
        assertEquals(new BlockPos(1,5,0),path.steps.get(path.steps.size()-1).pos);
    }

    @Test public void gatePlannerFindsGrateBelowApproximateMarker(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(1,5,0));
        world.support.add(new BlockPos(1,6,0));
        // The iron-bar floor is one block below the feet plane; the marker is three blocks high.
        world.iron.add(new BlockPos(-1,4,0));
        world.iron.add(new BlockPos(1,4,0));
        world.iron.add(new BlockPos(0,4,-1));
        world.iron.add(new BlockPos(0,4,1));

        GatePlan gate=new GatePlanner().find(world,new BlockPos(0,8,0),new BlockPos(1,6,0),1,8);
        assertNotNull(gate);
        assertEquals("planner should choose the real grate feet plane, not the marker height",5,gate.planeY);
        assertTrue(gate.columns.contains(new BlockPos(0,5,0)));
    }

    @Test public void gatePlannerRejectsAnUnframedOrBlockedColumn(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(1,5,0));
        GatePlan noFrame=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(1,6,0),1,8);
        assertNull(noFrame);

        world.iron.add(new BlockPos(-1,4,0));
        world.iron.add(new BlockPos(1,4,0));
        world.iron.add(new BlockPos(0,4,-1));
        world.iron.add(new BlockPos(0,4,1));
        world.blocked.add(new BlockPos(0,5,0));
        GatePlan blocked=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(1,6,0),1,8);
        assertNull(blocked);
    }

    @Test public void supportsStraightDownDropThroughOneBlockOpening(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(0,5,0));
        world.support.add(new BlockPos(0,0,0));
        PathResult result=new Pathfinder().find(world,new BlockPos(0,5,0),
                java.util.Collections.singletonList(new BlockPos(0,0,0)),1000,8);
        assertTrue(result.found());
        assertEquals(2,result.steps.size());
        assertEquals(MovementKind.DROP,result.steps.get(1).kind);
        assertEquals(0.0D,result.steps.get(1).cost,0.0001D);
    }

    private static final class TestWorld implements PathWorld {
        final Set<BlockPos> support=new HashSet<BlockPos>();
        final Set<BlockPos> iron=new HashSet<BlockPos>();
        final Set<BlockPos> blocked=new HashSet<BlockPos>();
        TestWorld(){for(int x=-2;x<=10;x++)for(int z=-2;z<=10;z++)support.add(new BlockPos(x,1,z));}
        public boolean canStand(BlockPos p){return p.y>=0&&p.y<8&&support.contains(p)&&!blocked.contains(p);}
        public boolean clear(BlockPos p){return p.y>=0&&p.y<10&&!blocked.contains(p)&&!iron.contains(p);}
        public boolean hazard(BlockPos p){return false;}
        public boolean climbable(BlockPos p){return false;}
        public boolean liquid(BlockPos p){return false;}
        public boolean ironBars(BlockPos p){return iron.contains(p);}
        public double extraCost(BlockPos p){return 0D;}
        public boolean jumpArcClear(BlockPos a,BlockPos b){return true;}
        public boolean lineClear(BlockPos a,BlockPos b){return a.y==b.y&&clear(a)&&clear(b);}
    }
}
