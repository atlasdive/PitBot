package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class StairLanePreferenceTest {
    @Test
    public void ascendingStairsPreferCenterLaneWhenBothLanesAreUsable() {
        LaneWorld world = new LaneWorld();
        PathNode start = new PathNode(0, 2, 0, PathNode.EAST);
        for (int x = 0; x <= 3; x++) {
            int y2 = 2 + x;
            world.add(new PathNode(x, y2, 0, PathNode.CENTER));
            world.add(new PathNode(x, y2, 0, PathNode.EAST));
        }

        PathResult result = new Pathfinder().findNodes(world, start, Arrays.asList(
                new PathNode(3, 5, 0, PathNode.EAST),
                new PathNode(3, 5, 0, PathNode.CENTER)), 1000, 8);

        assertTrue("the stair route should be found", result.found());
        assertEquals("the planner should finish in the centre lane",
                PathNode.CENTER, result.steps.get(result.steps.size() - 1).node.lane);
        for (PathStep step : result.steps) {
            if (step.node.x > 0) {
                assertEquals("once on the stair, prefer its centre line",
                        PathNode.CENTER, step.node.lane);
            }
        }
    }

    @Test
    public void descendingStairsPreferCenterLaneWhenBothLanesAreUsable() {
        LaneWorld world = new LaneWorld();
        PathNode start = new PathNode(0, 5, 0, PathNode.EAST);
        for (int x = 0; x <= 3; x++) {
            int y2 = 5 - x;
            world.add(new PathNode(x, y2, 0, PathNode.CENTER));
            world.add(new PathNode(x, y2, 0, PathNode.EAST));
        }

        PathResult result = new Pathfinder().findNodes(world, start, Arrays.asList(
                new PathNode(3, 2, 0, PathNode.EAST),
                new PathNode(3, 2, 0, PathNode.CENTER)), 1000, 8);

        assertTrue("the stair route should be found", result.found());
        assertEquals("the planner should finish in the centre lane",
                PathNode.CENTER, result.steps.get(result.steps.size() - 1).node.lane);
        for (PathStep step : result.steps) {
            if (step.node.x > 0) {
                assertEquals("once on the stair, prefer its centre line",
                        PathNode.CENTER, step.node.lane);
            }
        }
    }

    @Test
    public void keepsUsingSupportedEdgeLaneWhenCenterIsUnavailable() {
        LaneWorld world = new LaneWorld();
        PathNode start = new PathNode(0, 2, 0, PathNode.EAST);
        for (int x = 0; x <= 3; x++) {
            world.add(new PathNode(x, 2 + x, 0, PathNode.EAST));
        }

        PathResult result = new Pathfinder().findNodes(world, start,
                Arrays.asList(new PathNode(3, 5, 0, PathNode.EAST)), 1000, 8);

        assertTrue("the supported edge route should remain available", result.found());
        for (PathStep step : result.steps) {
            assertEquals("the planner must not invent an unsupported centre lane",
                    PathNode.EAST, step.node.lane);
        }
    }

    private static final class LaneWorld implements PathWorld {
        private final Set<PathNode> stands = new HashSet<PathNode>();

        void add(PathNode node) {
            stands.add(node);
        }

        @Override public boolean canStand(PathNode pos) {
            return stands.contains(pos);
        }

        @Override public boolean clear(PathNode pos) {
            return true;
        }

        @Override public boolean hazard(PathNode pos) {
            return false;
        }

        @Override public double extraCost(PathNode pos) {
            return 0.0D;
        }

        @Override public boolean transitionClear(PathNode from, PathNode to) {
            return true;
        }

        @Override public boolean jumpArcClear(PathNode from, PathNode to) {
            return true;
        }

        @Override public boolean lineClear(PathNode from, PathNode to) {
            return from.y2 == to.y2 && from.lane == to.lane;
        }

        @Override public boolean canStand(BlockPos pos) {
            return false;
        }

        @Override public boolean clear(BlockPos pos) {
            return true;
        }

        @Override public boolean hazard(BlockPos pos) {
            return false;
        }

        @Override public boolean climbable(BlockPos pos) {
            return false;
        }

        @Override public boolean liquid(BlockPos pos) {
            return false;
        }

        @Override public boolean gateBarrier(BlockPos pos) {
            return false;
        }

        @Override public double extraCost(BlockPos pos) {
            return 0.0D;
        }

        @Override public boolean jumpArcClear(BlockPos from, BlockPos to) {
            return true;
        }

        @Override public boolean lineClear(BlockPos from, BlockPos to) {
            return from.y == to.y;
        }
    }
}
