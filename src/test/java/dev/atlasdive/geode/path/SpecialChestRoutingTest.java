package dev.atlasdive.geode.path;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpecialChestRoutingTest {
    @Test public void longChestRouteUsesShorterLoadedChunkFriendlySegments() {
        BlockPos chest = new BlockPos(80, 60, 66);
        BlockPos first = ChestRoutePlanner.nextWaypoint(0.5D, 0.5D, chest, 24, 60);
        assertNotNull(first);
        double segment = ChestRoutePlanner.horizontalDistance(
                0.5D, 0.5D, first.x + 0.5D, first.z + 0.5D);
        assertTrue("intermediate path segment must stay within its 24-block budget",
                segment <= 25.0D);
        assertTrue(ChestRoutePlanner.makesProgress(0.5D, 0.5D,
                first.x + 0.5D, first.z + 0.5D, chest, 2.0D));
    }

    @Test public void transitGoalMustActuallyMoveCloserToTarget() {
        BlockPos target = new BlockPos(80, 60, 66);
        assertFalse(ChestRoutePlanner.makesProgress(
                40.5D, 60.5D, 40.5D, 60.5D, target, 2.0D));
        assertFalse(ChestRoutePlanner.makesProgress(
                40.5D, 60.5D, 35.5D, 60.5D, target, 2.0D));
        assertTrue(ChestRoutePlanner.makesProgress(
                40.5D, 60.5D, 50.5D, 60.5D, target, 2.0D));
    }

    @Test public void transitSearchVolumeExpandsHorizontallyAndVertically() {
        BlockPos waypoint = new BlockPos(36, 58, 33);
        List<BlockPos> compact = ChestRoutePlanner.transitCandidates(waypoint, 2, 3);
        assertEquals(175, compact.size());
        assertTrue(compact.contains(new BlockPos(34, 55, 31)));
        assertTrue(compact.contains(new BlockPos(38, 61, 35)));

        List<BlockPos> expanded = ChestRoutePlanner.transitCandidates(waypoint, 6, 9);
        assertEquals(3211, expanded.size());
        assertTrue(expanded.contains(new BlockPos(30, 49, 27)));
        assertTrue(expanded.contains(new BlockPos(42, 67, 39)));
    }

    @Test public void screenshotTransitAreaIsNotRejectedByTheProgressFilter() {
        BlockPos chest = new BlockPos(18, 53, 29);
        BlockPos waypoint = ChestRoutePlanner.nextWaypoint(48.5D, 36.5D, chest, 12, 58);
        assertEquals(new BlockPos(36, 58, 33), waypoint);
        for (BlockPos candidate : ChestRoutePlanner.transitCandidates(waypoint, 2, 3)) {
            assertTrue("Every cell in the screenshot's transit box should advance toward the chest",
                    ChestRoutePlanner.makesProgress(48.5D, 36.5D,
                            candidate.x + 0.5D, candidate.z + 0.5D, chest, 3.6D));
        }
    }

    @Test public void specialChestApproachHasSeveralStandableCellsAroundTarget() {
        BlockPos chest = new BlockPos(99, 61, 46);
        List<PathNode> goals = SpecialChestTarget.approachGoals(chest);
        assertEquals(40, goals.size());
        Set<String> positions = new HashSet<String>();
        for (PathNode goal : goals) {
            assertEquals(116, goal.y2);
            assertFalse("the chest's occupied block cannot be a standing goal",
                    goal.x == chest.x && goal.z == chest.z);
            assertTrue(goal.x >= 98 && goal.x <= 100);
            assertTrue(goal.z >= 46 && goal.z <= 48);
            positions.add(goal.x + ":" + goal.y2 + ":" + goal.z + ":" + goal.lane);
        }
        assertEquals("approach goals must be unique", goals.size(), positions.size());
    }

    @Test public void allConfiguredSpecialChestHeightsKeepTheirApproachHeight() {
        for (int y = 61; y <= 63; y++) {
            BlockPos chest = new BlockPos(99, y, 46);
            assertTrue(SpecialChestTarget.matches(chest));
            for (PathNode goal : SpecialChestTarget.approachGoals(chest))
                assertEquals((y - 3) * 2, goal.y2);
        }
    }
}
