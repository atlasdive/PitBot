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
