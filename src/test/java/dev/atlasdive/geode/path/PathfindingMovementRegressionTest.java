package dev.atlasdive.geode.path;

import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class PathfindingMovementRegressionTest {
    @Test public void vinePathToHigherGoalIsStrictlyAscending() {
        VerticalVineWorld world = new VerticalVineWorld();
        PathNode start = new PathNode(0, 2, 0, PathNode.NORTH);
        PathNode goal = new PathNode(0, 12, 0, PathNode.NORTH);
        PathResult result = new Pathfinder().findNodes(world, start,
                Collections.singletonList(goal), 2000, 8);
        assertTrue("pathfinder should reach the top of the vine", result.found());
        int previous = start.y2;
        for (PathStep step : result.steps) {
            assertTrue("ascending vine route must never step down", step.node.y2 >= previous);
            previous = step.node.y2;
        }
        assertEquals(goal, result.steps.get(result.steps.size()-1).node);
    }

    @Test public void vinePathToLowerGoalIsStrictlyDescending() {
        VerticalVineWorld world = new VerticalVineWorld();
        PathNode start = new PathNode(0, 12, 0, PathNode.NORTH);
        PathNode goal = new PathNode(0, 2, 0, PathNode.NORTH);
        PathResult result = new Pathfinder().findNodes(world, start,
                Collections.singletonList(goal), 2000, 8);
        assertTrue(result.found());
        int previous = start.y2;
        for (PathStep step : result.steps) {
            assertTrue("descending vine route must never step back up", step.node.y2 <= previous);
            previous = step.node.y2;
        }
        assertEquals(goal, result.steps.get(result.steps.size()-1).node);
    }

    @Test public void stairOrSlabCellsKeepTheirCentreWaypoint() {
        SurfaceWorld world = new SurfaceWorld();
        PathNode start = new PathNode(0, 2, 0, PathNode.CENTER);
        PathNode middle = new PathNode(1, 2, 0, PathNode.CENTER);
        PathNode goal = new PathNode(2, 2, 0, PathNode.CENTER);
        PathResult result = new Pathfinder().findNodes(world, start,
                Collections.singletonList(goal), 1000, 8);
        assertTrue(result.found());
        PathStep centreStep = null;
        for (PathStep step : result.steps) {
            if (step.node.x == 1) {
                assertEquals("side lanes are forbidden on the stair/slab cell",
                        PathNode.CENTER, step.node.lane);
                centreStep = step;
            }
        }
        assertNotNull("the path must include the stair/slab centre", centreStep);
        assertTrue("centre-only marker must be passed to movement control", centreStep.centerOnly);
    }

    private static final class VerticalVineWorld implements PathWorld {
        public boolean canStand(BlockPos p){return p.x==0&&p.z==0&&p.y>=0&&p.y<=8;}
        public boolean canStand(PathNode p){return p.x==0&&p.z==0&&p.y2>=0&&p.y2<=16&&p.lane==PathNode.NORTH;}
        public boolean clear(BlockPos p){return true;}
        public boolean clear(PathNode p){return true;}
        public boolean hazard(BlockPos p){return false;}
        public boolean hazard(PathNode p){return false;}
        public boolean climbable(BlockPos p){return p.x==0&&p.z==0;}
        public boolean climbable(PathNode p){return p.x==0&&p.z==0;}
        public boolean liquid(BlockPos p){return false;}
        public boolean gateBarrier(BlockPos p){return false;}
        public double extraCost(BlockPos p){return 0D;}
        public double extraCost(PathNode p){return 0D;}
        public boolean jumpArcClear(BlockPos a,BlockPos b){return false;}
        public boolean jumpArcClear(PathNode a,PathNode b){return false;}
        public boolean lineClear(BlockPos a,BlockPos b){return a.y==b.y;}
        public boolean lineClear(PathNode a,PathNode b){return a.y2==b.y2&&a.lane==b.lane;}
        public boolean transitionClear(PathNode a,PathNode b){return true;}
    }

    private static final class SurfaceWorld implements PathWorld {
        public boolean canStand(BlockPos p){return p.y==1&&p.x>=0&&p.x<=2&&p.z==0;}
        public boolean canStand(PathNode p){
            return p.y2==2&&p.x>=0&&p.x<=2&&p.z==0
                    &&(p.x!=1||p.lane==PathNode.CENTER);
        }
        public boolean centerOnly(PathNode p){return p.x==1&&p.z==0&&p.y2==2;}
        public boolean clear(BlockPos p){return true;}
        public boolean hazard(BlockPos p){return false;}
        public boolean climbable(BlockPos p){return false;}
        public boolean liquid(BlockPos p){return false;}
        public boolean gateBarrier(BlockPos p){return false;}
        public double extraCost(BlockPos p){return 0D;}
        public boolean jumpArcClear(BlockPos a,BlockPos b){return false;}
        public boolean lineClear(BlockPos a,BlockPos b){return a.y==b.y;}
        public boolean transitionClear(PathNode a,PathNode b){return true;}
        public boolean lineClear(PathNode a,PathNode b){return a.y2==b.y2;}
    }
}
