package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class HalfBlockCenterPathTest {
    @Test
    public void horizontalHalfBlockRouteMovesTowardCenterInsteadOfHuggingEdge() {
        SlabWorld world = new SlabWorld();
        PathNode start = new PathNode(0, 129, 0, PathNode.EAST);
        for (int x = 0; x <= 4; x++) {
            // Model a half-slab top at Y=64.5: both the centre and east side
            // collision lanes are supported, so the centre is the safer route.
            world.add(new PathNode(x, 129, 0, PathNode.CENTER));
            world.add(new PathNode(x, 129, 0, PathNode.EAST));
        }

        PathResult result = new Pathfinder().findNodes(world, start, Arrays.asList(
                new PathNode(4, 129, 0, PathNode.EAST),
                new PathNode(4, 129, 0, PathNode.CENTER)), 1000, 8);

        assertTrue("the half-block platform route should be found", result.found());
        assertEquals(PathNode.CENTER, result.steps.get(result.steps.size() - 1).node.lane);
        for (PathStep step : result.steps) {
            if (step.node.x > 0) {
                assertEquals("the path should use the middle of the half-block surface",
                        PathNode.CENTER, step.node.lane);
            }
        }
    }

    @Test
    public void narrowSurfaceStillUsesEdgeWhenItsCenterIsNotSupported() {
        SlabWorld world = new SlabWorld();
        PathNode start = new PathNode(0, 129, 0, PathNode.EAST);
        for (int x = 0; x <= 4; x++) {
            world.add(new PathNode(x, 129, 0, PathNode.EAST));
        }

        PathResult result = new Pathfinder().findNodes(world, start,
                Arrays.asList(new PathNode(4, 129, 0, PathNode.EAST)), 1000, 8);

        assertTrue("the narrow supported route should remain traversable", result.found());
        for (PathStep step : result.steps) {
            assertEquals("do not force the player onto an unsupported centre",
                    PathNode.EAST, step.node.lane);
        }
    }

    private static final class SlabWorld implements PathWorld {
        private final Set<PathNode> supported = new HashSet<PathNode>();

        void add(PathNode node) { supported.add(node); }

        @Override public boolean canStand(PathNode pos) { return supported.contains(pos); }
        @Override public boolean clear(PathNode pos) { return true; }
        @Override public boolean hazard(PathNode pos) { return false; }
        @Override public double extraCost(BlockPos pos) { return 0.0D; }
        @Override public boolean climbable(BlockPos pos) { return false; }
        @Override public boolean liquid(BlockPos pos) { return false; }
        @Override public boolean gateBarrier(BlockPos pos) { return false; }
        @Override public boolean jumpArcClear(BlockPos from, BlockPos to) { return true; }
        @Override public boolean lineClear(BlockPos from, BlockPos to) { return from.y == to.y; }
        @Override public boolean transitionClear(PathNode from, PathNode to) { return true; }
        @Override public boolean jumpArcClear(PathNode from, PathNode to) { return true; }
        @Override public boolean lineClear(PathNode from, PathNode to) {
            return from.y2 == to.y2 && from.lane == to.lane;
        }
        @Override public boolean canStand(BlockPos pos) { return false; }
        @Override public boolean clear(BlockPos pos) { return true; }
        @Override public boolean hazard(BlockPos pos) { return false; }
        @Override public double extraCost(PathNode pos) { return 0.0D; }
    }
}
