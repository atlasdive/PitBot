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

    @Test public void patrolPlannerContinuesBeyondFormer150BlockBoundary(){
        BlockPos center=new BlockPos(47,66,75);
        BlockPos distant=PatrolPlanner.waypoint(center,80,66);
        long dx=(long)distant.x-center.x,dz=(long)distant.z-center.z;
        assertTrue("the expanding spiral must continue past the old 150-block limit",
                dx*dx+dz*dz>150L*150L);
    }

    @Test public void onlyTheConfiguredChestAreaIsExcluded(){
        assertTrue(ChestExclusion.isExcluded(19,66,72));
        assertTrue(ChestExclusion.isExcluded(23,70,82));
        assertTrue(ChestExclusion.isExcluded(21,68,77));
        assertFalse(ChestExclusion.isExcluded(18,68,77));
        assertFalse(ChestExclusion.isExcluded(24,68,77));
        assertFalse(ChestExclusion.isExcluded(21,65,77));
        assertFalse(ChestExclusion.isExcluded(21,71,77));
        assertFalse(ChestExclusion.isExcluded(21,68,71));
        assertFalse(ChestExclusion.isExcluded(21,68,83));
        assertFalse("distant chests are eligible without particle evidence",ChestExclusion.isExcluded(800,60,-600));
    }

    @Test public void chestLootTimerAllowsOneClickEveryTwoTicks(){
        ChestLootTimer timer=new ChestLootTimer();
        assertFalse(timer.isReady());
        assertTrue(timer.isReady());
        assertFalse(timer.isReady());
        assertTrue(timer.isReady());
        timer.reset();
        assertFalse(timer.isReady());
        assertTrue(timer.isReady());
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

    @Test public void elevatedSewerChestUsesSevenBlockScaffoldPlan(){
        ChestScaffoldPlan plan=ChestScaffoldPlan.forChest(new BlockPos(80,60,66));
        assertNotNull(plan);
        assertEquals(new BlockPos(81,53,63),plan.getStagingFeet());
        assertEquals(7,plan.getPlacementCount());
        assertEquals(new BlockPos(81,53,63),plan.getPlacedBlock(0));
        assertEquals(new BlockPos(81,59,63),plan.getPlacedBlock(6));
        assertEquals(new BlockPos(81,58,63),plan.getSupportBlock(6));
        assertEquals(new BlockPos(81,60,63),plan.getTopStandingPosition());
        assertFalse("the original 3-block radius is just short of the scaffold position",
                ChestApproachPlanner.candidates(new BlockPos(80,60,66),3,3).contains(plan.getTopStandingPosition()));
        assertTrue("the scaffold position must be included after expanding the approach radius to 4",
                ChestApproachPlanner.candidates(new BlockPos(80,60,66),4,3).contains(plan.getTopStandingPosition()));
        assertNull("the scaffold route must not apply to unrelated chests",
                ChestScaffoldPlan.forChest(new BlockPos(80,60,65)));
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


    @Test public void pathfinderClimbsAndDescendsHalfBlockStairSteps(){
        StairWorld world=new StairWorld();
        PathNode lower=new PathNode(0,2,0);
        PathNode middle=new PathNode(1,3,0);
        PathNode upper=new PathNode(2,4,0);
        PathResult up=new Pathfinder().findNodes(world,lower,java.util.Collections.singletonList(upper),1000,8);
        assertTrue("A* should route through the half-block stair tread",up.found());
        assertEquals(3,up.steps.size());
        assertEquals(middle,up.steps.get(1).node);
        assertEquals(MovementKind.STEP_UP,up.steps.get(1).kind);
        assertEquals(upper,up.steps.get(2).node);
        assertEquals(MovementKind.STEP_UP,up.steps.get(2).kind);
        PathResult down=new Pathfinder().findNodes(world,upper,java.util.Collections.singletonList(lower),1000,8);
        assertTrue("A* should descend the same stair treads",down.found());
        assertEquals(3,down.steps.size());
        assertEquals(middle,down.steps.get(1).node);
        assertEquals(MovementKind.STEP_DOWN,down.steps.get(1).kind);
        assertEquals(lower,down.steps.get(2).node);
        assertEquals(MovementKind.STEP_DOWN,down.steps.get(2).kind);
    }

    @Test public void pathStepPreservesHalfBlockFeetHeight(){
        PathStep step=new PathStep(new PathNode(4,133,7),MovementKind.STEP_UP,1.2D);
        assertEquals(66.5D,step.feetY,0.0001D);
        assertEquals(new BlockPos(4,66,7),step.pos);
    }

    private static final class StairWorld extends TestWorld {
        final Set<PathNode> stands=new HashSet<PathNode>();
        StairWorld(){
            stands.add(new PathNode(0,2,0));stands.add(new PathNode(1,3,0));stands.add(new PathNode(2,4,0));
        }
        @Override public boolean canStand(PathNode p){return stands.contains(p);}
        @Override public boolean clear(PathNode p){return true;}
        @Override public boolean hazard(PathNode p){return false;}
        @Override public double extraCost(PathNode p){return 0D;}
        @Override public boolean jumpArcClear(PathNode a,PathNode b){return true;}
        @Override public boolean lineClear(PathNode a,PathNode b){return a.y2==b.y2;}
    }

    @Test public void choosesAnActuallySupportedSideLaneInsteadOfUnsupportedCellCenter(){
        LaneWorld world=new LaneWorld();
        PathNode start=new PathNode(0,2,0,PathNode.CENTER);
        PathNode stairLane=new PathNode(1,2,0,PathNode.EAST);
        PathNode goal=new PathNode(2,2,0,PathNode.CENTER);
        world.stands.add(start);world.stands.add(stairLane);world.stands.add(goal);
        PathResult result=new Pathfinder().findNodes(world,start,
                java.util.Collections.singletonList(goal),1000,8);
        assertTrue(result.found());
        assertTrue("path must use the supported side of the stair, not its unsupported centre",
                result.steps.stream().anyMatch(step->step.node.equals(stairLane)));
        assertEquals(goal,result.steps.get(result.steps.size()-1).node);
    }

    @Test public void canPathThroughWaterAndContinueToDryGround(){
        WaterWorld world=new WaterWorld();
        PathNode start=new PathNode(0,2,0);
        PathNode water=new PathNode(1,2,0);
        PathNode shore=new PathNode(2,2,0);
        world.stands.add(start);world.stands.add(water);world.stands.add(shore);
        world.wet.add(start);world.wet.add(water);
        PathResult result=new Pathfinder().findNodes(world,start,
                java.util.Collections.singletonList(shore),1000,8);
        assertTrue("water cells must be valid path nodes",result.found());
        assertTrue("the water segment should be marked for swimming",
                result.steps.stream().anyMatch(step->step.node.equals(water)&&step.kind==MovementKind.SWIM));
        assertEquals("the transition from water onto dry shore is also traversed as a swim edge",
                MovementKind.SWIM,result.steps.get(result.steps.size()-1).kind);
        assertEquals(shore,result.steps.get(result.steps.size()-1).node);
        assertTrue("a reached swim waypoint must advance to the following dry-ground waypoint",
                WaypointGeometry.hasReachedWaypoint(0.20D,0.10D,0.52D));
        assertFalse("a swim waypoint must not advance while still too far away",
                WaypointGeometry.hasReachedWaypoint(0.80D,0.10D,0.52D));
        assertFalse("a swim waypoint must not advance while vertically misaligned",
                WaypointGeometry.hasReachedWaypoint(0.20D,0.50D,0.52D));
    }

    @Test public void pathNodeKeepsItsActualHorizontalLane(){
        PathNode node=new PathNode(10,133,20,PathNode.WEST);
        assertEquals(10.15D,node.worldX(),0.0001D);
        assertEquals(20.5D,node.worldZ(),0.0001D);
        assertEquals(PathNode.WEST,PathNode.atPlayer(node.worldX(),66.5D,node.worldZ()).lane);
    }

    private static final class LaneWorld extends TestWorld {
        final Set<PathNode> stands=new HashSet<PathNode>();
        @Override public boolean canStand(PathNode p){return stands.contains(p);}
        @Override public boolean clear(PathNode p){return true;}
        @Override public boolean hazard(PathNode p){return false;}
        @Override public double extraCost(PathNode p){return 0D;}
        @Override public boolean jumpArcClear(PathNode a,PathNode b){return false;}
        @Override public boolean transitionClear(PathNode a,PathNode b){return true;}
        @Override public boolean lineClear(PathNode a,PathNode b){return a.y2==b.y2&&a.lane==b.lane;}
    }

    private static final class WaterWorld extends TestWorld {
        final Set<PathNode> stands=new HashSet<PathNode>();
        final Set<PathNode> wet=new HashSet<PathNode>();
        @Override public boolean canStand(PathNode p){return stands.contains(p);}
        @Override public boolean clear(PathNode p){return true;}
        @Override public boolean hazard(PathNode p){return false;}
        @Override public boolean liquid(PathNode p){return wet.contains(p);}
        @Override public double extraCost(PathNode p){return 0D;}
        @Override public boolean jumpArcClear(PathNode a,PathNode b){return false;}
        @Override public boolean transitionClear(PathNode a,PathNode b){return true;}
        @Override public boolean lineClear(PathNode a,PathNode b){return a.y2==b.y2&&a.lane==b.lane;}
    }

    @Test public void detectsWhenPlayerHasPassedWaypointInsteadOfTurningBack(){
        assertTrue(WaypointGeometry.hasPassedCheckpoint(0,0,5,0,5.25,0.10,0.90));
        assertFalse(WaypointGeometry.hasPassedCheckpoint(0,0,5,0,4.70,0.0,0.90));
        assertFalse("a wide miss must not count as crossing the checkpoint",
                WaypointGeometry.hasPassedCheckpoint(0,0,5,0,5.25,1.5,0.90));
    }

    @Test public void sharpTurnDisablesSprintOnlyNearCheckpoint(){
        assertFalse(WaypointGeometry.isSharpTurn(0,0,5,0,10,0,38.0D));
        assertTrue(WaypointGeometry.isSharpTurn(0,0,5,0,5,5,38.0D));
        assertTrue(WaypointGeometry.shouldSprint(5.0D,true));
        assertFalse(WaypointGeometry.shouldSprint(2.0D,true));
        assertFalse(WaypointGeometry.shouldSprint(1.0D,false));
    }


    @Test public void watchAreaIncludesItsConfiguredBounds(){
        assertTrue(WatchArea.contains(11.0D,58.0D,12.0D));
        assertTrue(WatchArea.contains(104.99D,61.99D,26.99D));
        assertFalse(WatchArea.contains(10.99D,60.0D,20.0D));
        assertFalse(WatchArea.contains(50.0D,62.0D,20.0D));
        assertFalse(WatchArea.contains(50.0D,60.0D,27.0D));
    }

    @Test public void deathRecapDetectorAcceptsVariableNamesAndFormatting(){
        assertTrue(DeathMessageDetector.matches("§cDEATH! by [120] ultralighttx VIEW RECAP"));
        assertTrue(DeathMessageDetector.matches("DEATH! by AnotherPlayer VIEW RECAP"));
        assertFalse(DeathMessageDetector.matches("DEATH! by AnotherPlayer"));
        assertFalse(DeathMessageDetector.matches("VIEW RECAP"));
    }

    @Test public void chestAimIsCalculatedFromActualPlayerPosition(){
        float yaw=ChestAimMath.yaw(99.0D,47.5D,99.5D,47.0D);
        float pitch=ChestAimMath.pitch(99.0D,59.62D,47.5D,99.5D,61.48D,47.0D);
        assertTrue("aim should point broadly north toward the special chest",yaw<-120.0F&&yaw>-200.0F);
        assertTrue("aim should look upward toward the elevated chest",pitch<0.0F&&pitch>-80.0F);
    }

    @Test public void vineFacesMapToSupportedClimbLanes(){
        int mask=VineTraversal.laneMask(true,false,false,true);
        assertTrue((mask&(1<<PathNode.SOUTH))!=0);
        assertTrue((mask&(1<<PathNode.EAST))!=0);
        assertFalse((mask&(1<<PathNode.WEST))!=0);
        assertEquals(1<<PathNode.CENTER,VineTraversal.laneMask(false,false,false,false));
        assertEquals(0.0F,VineTraversal.yawForLane(PathNode.NORTH,45.0F),0.001F);
        assertEquals(-90.0F,VineTraversal.yawForLane(PathNode.WEST,45.0F),0.001F);
        assertEquals(-180.0F,VineTraversal.yawForLane(PathNode.SOUTH,45.0F),0.001F);
        assertEquals(90.0F,VineTraversal.yawForLane(PathNode.EAST,45.0F),0.001F);
    }

    @Test public void vineClimbPrefersFullBlockVerticalSteps(){
        VineWorld world=new VineWorld();
        PathNode start=new PathNode(0,2,0,PathNode.NORTH);
        PathNode goal=new PathNode(0,6,0,PathNode.NORTH);
        PathResult result=new Pathfinder().findNodes(world,start,
                java.util.Collections.singletonList(goal),1000,8);
        assertTrue(result.found());
        assertEquals(3,result.steps.size());
        assertEquals(new PathNode(0,4,0,PathNode.NORTH),result.steps.get(1).node);
        assertEquals(MovementKind.CLIMB,result.steps.get(1).kind);
    }


    private static final class VineWorld extends TestWorld {
        @Override public boolean canStand(PathNode p){
            return p.x==0&&p.z==0&&p.lane==PathNode.NORTH&&p.y2>=2&&p.y2<=6;
        }
        @Override public boolean clear(PathNode p){return true;}
        @Override public boolean climbable(PathNode p){return p.x==0&&p.z==0&&p.lane==PathNode.NORTH;}
        @Override public boolean hazard(PathNode p){return false;}
        @Override public boolean liquid(PathNode p){return false;}
        @Override public double extraCost(PathNode p){return 0.0D;}
        @Override public boolean jumpArcClear(PathNode a,PathNode b){return false;}
        @Override public boolean transitionClear(PathNode a,PathNode b){return true;}
        @Override public boolean lineClear(PathNode a,PathNode b){return false;}
    }

    private static class TestWorld implements PathWorld {
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
