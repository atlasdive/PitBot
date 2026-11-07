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

    @Test public void gatePlannerProvidesApproachUpToFourBlocksFromOpening(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(4,5,0));
        world.support.add(new BlockPos(4,6,0));
        world.solid.add(new BlockPos(-1,4,0));
        world.solid.add(new BlockPos(1,4,0));
        world.solid.add(new BlockPos(0,4,-1));
        world.solid.add(new BlockPos(0,4,1));

        GatePlan gate=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(4,6,0),4,8);
        assertNotNull(gate);
        BlockPos approach=new BlockPos(4,5,0);
        assertTrue("an approach four blocks away with clear line-of-sight should be accepted",
                gate.approaches.contains(approach));
        assertEquals(new BlockPos(0,5,0),gate.approachToAperture.get(approach));
        PathResult route=new Pathfinder().find(world,new BlockPos(4,6,0),gate.approaches,1000,8);
        assertTrue("A* should find the safe approach point",route.found());
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

    @Test public void gatePlannerAcceptsGenericSolidFrameNotJustIronBars(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(1,5,0));
        world.support.add(new BlockPos(1,6,0));
        // These represent solid decorative blocks from a remake map, not iron bars.
        world.solid.add(new BlockPos(-1,4,0));
        world.solid.add(new BlockPos(1,4,0));

        GatePlan gate=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(1,6,0),1,8);
        assertNotNull("a two-sided opening framed by solid blocks should be detected",gate);
        assertTrue(gate.approaches.contains(new BlockPos(1,5,0)));
    }

    @Test public void gatePlannerSearchesBeyondFourBlocks(){
        TestWorld world=new TestWorld();
        world.support.clear();
        world.support.add(new BlockPos(9,5,0));
        world.support.add(new BlockPos(9,6,0));
        world.solid.add(new BlockPos(7,4,0));
        world.solid.add(new BlockPos(9,4,0));

        GatePlan gate=new GatePlanner().find(world,new BlockPos(0,5,0),new BlockPos(9,6,0),12,8);
        assertNotNull("opening at X=8 should be found from a marker at X=0 with radius 12",gate);
        assertTrue(gate.approaches.contains(new BlockPos(9,5,0)));
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

    @Test public void sewerPatrolUsesAnExpandingNonRepeatingSpiral(){
        BlockPos origin=new BlockPos(40,66,75);
        Set<BlockPos> visited=new HashSet<BlockPos>();
        BlockPos previous=origin;
        for(int leg=0;leg<16;leg++){
            BlockPos next=PatrolPlanner.waypoint(origin,leg,66);
            assertTrue("patrol must never revisit the same waypoint",visited.add(next));
            assertFalse("patrol must not return to its origin",next.equals(origin));
            long dx=(long)next.x-previous.x,dz=(long)next.z-previous.z;
            assertTrue("each patrol leg must make real progress",dx*dx+dz*dz>=64L);
            previous=next;
        }
    }

    @Test public void patrolPlannerUsesAProperExpandingSquareSpiral(){
        BlockPos origin=new BlockPos(47,66,75);
        assertEquals(new BlockPos(47,66,67),PatrolPlanner.waypoint(origin,0,66));
        assertEquals(new BlockPos(55,66,67),PatrolPlanner.waypoint(origin,1,66));
        assertEquals(new BlockPos(55,66,83),PatrolPlanner.waypoint(origin,2,66));
        assertEquals(new BlockPos(39,66,83),PatrolPlanner.waypoint(origin,3,66));
        assertEquals(new BlockPos(39,66,59),PatrolPlanner.waypoint(origin,4,66));
    }

    @Test public void patrolPlannerCanStopAtTheChestScanBoundary(){
        BlockPos center=new BlockPos(47,66,75);
        assertTrue(PatrolPlanner.insideHorizontalRadius(new BlockPos(50,66,90),center,64));
        assertTrue(PatrolPlanner.insideHorizontalRadius(new BlockPos(87,66,75),center,64));
        assertFalse(PatrolPlanner.insideHorizontalRadius(new BlockPos(112,66,75),center,64));
    }

    @Test public void chestScanRangeUses150BlockCircularRadius(){
        assertTrue(ChestScanRange.contains(197,75,47,75,150));
        assertTrue(ChestScanRange.contains(47,225,47,75,150));
        assertFalse(ChestScanRange.contains(198,75,47,75,150));
        assertFalse(ChestScanRange.contains(47,226,47,75,150));
        assertFalse("diagonal points outside a 150-block circle must be excluded",
                ChestScanRange.contains(167,175,47,75,150));
    }

    @Test public void distantChestRoutesAreSplitInto56BlockSegments(){
        BlockPos target=new BlockPos(197,69,75);
        BlockPos first=ChestRoutePlanner.nextWaypoint(47.5D,75.5D,target,56,66);
        assertNotNull(first);
        double firstDistance=Math.sqrt(
                (first.x+.5D-47.5D)*(first.x+.5D-47.5D)
                +(first.z+.5D-75.5D)*(first.z+.5D-75.5D));
        assertTrue(firstDistance<=56.0D);

        BlockPos second=ChestRoutePlanner.nextWaypoint(first.x+.5D,first.z+.5D,target,56,66);
        assertNotNull(second);
        double secondDistance=Math.sqrt(
                (second.x+.5D-first.x-.5D)*(second.x+.5D-first.x-.5D)
                +(second.z+.5D-first.z-.5D)*(second.z+.5D-first.z-.5D));
        assertTrue(secondDistance<=56.0D);

        BlockPos finalLeg=ChestRoutePlanner.nextWaypoint(second.x+.5D,second.z+.5D,target,56,66);
        assertNull("once within 56 blocks, use final chest approach candidates",finalLeg);
    }

    @Test public void chestApproachPlannerSearchesMultipleSidesAndHeights(){
        BlockPos chest=new BlockPos(20,69,75);
        java.util.List<BlockPos> candidates=ChestApproachPlanner.candidates(chest,3,3);
        assertEquals(196,candidates.size());
        assertFalse("the chest block itself must never be a standing goal",candidates.contains(chest));
        assertTrue(candidates.contains(chest.add(3,3,0)));
        assertTrue(candidates.contains(chest.add(-2,-3,1)));
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
        final Set<BlockPos> solid=new HashSet<BlockPos>();
        final Set<BlockPos> blocked=new HashSet<BlockPos>();
        TestWorld(){for(int x=-2;x<=10;x++)for(int z=-2;z<=10;z++)support.add(new BlockPos(x,1,z));}
        public boolean canStand(BlockPos p){return p.y>=0&&p.y<8&&support.contains(p)&&!blocked.contains(p);}
        public boolean clear(BlockPos p){return p.y>=0&&p.y<10&&!blocked.contains(p)&&!iron.contains(p);}
        public boolean hazard(BlockPos p){return false;}
        public boolean climbable(BlockPos p){return false;}
        public boolean liquid(BlockPos p){return false;}
        public boolean gateBarrier(BlockPos p){return iron.contains(p)||solid.contains(p);}
        public double extraCost(BlockPos p){return 0D;}
        public boolean jumpArcClear(BlockPos a,BlockPos b){return true;}
        public boolean lineClear(BlockPos a,BlockPos b){return a.y==b.y&&clear(a)&&clear(b);}
    }
}
