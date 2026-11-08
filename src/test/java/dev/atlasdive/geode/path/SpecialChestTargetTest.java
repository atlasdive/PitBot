package dev.atlasdive.geode.path;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpecialChestTargetTest {
    @Test public void acceptsAllThreeConfiguredVerticalPositions() {
        for (int y = 61; y <= 63; y++) {
            BlockPos chest = new BlockPos(99, y, 46);
            assertTrue("Y=" + y + " should be recognized as the special chest",
                    SpecialChestTarget.matches(chest));
            assertEquals(y - 3.0D, SpecialChestTarget.approachFeetY(chest), 0.0001D);
        }
    }

    @Test public void rejectsPositionsOutsideTheConfiguredColumnAndHeightRange() {
        assertFalse(SpecialChestTarget.matches(new BlockPos(99, 60, 46)));
        assertFalse(SpecialChestTarget.matches(new BlockPos(99, 64, 46)));
        assertFalse(SpecialChestTarget.matches(new BlockPos(98, 62, 46)));
        assertFalse(SpecialChestTarget.matches(new BlockPos(100, 62, 46)));
        assertFalse(SpecialChestTarget.matches(new BlockPos(99, 62, 45)));
        assertFalse(SpecialChestTarget.matches(new BlockPos(99, 62, 47)));
        assertFalse(SpecialChestTarget.matches(null));
    }

    @Test public void exposesEveryLaneForTheSpecialChestApproachCell() {
        for (int y = 61; y <= 63; y++) {
            List<PathNode> goals = SpecialChestTarget.approachGoals(new BlockPos(99, y, 46));
            assertEquals(PathNode.LANE_COUNT, goals.size());
            for (int lane = 0; lane < PathNode.LANE_COUNT; lane++) {
                PathNode goal = goals.get(lane);
                assertEquals(99, goal.x);
                assertEquals((y - 3) * 2, goal.y2);
                assertEquals(47, goal.z);
                assertEquals(lane, goal.lane);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsApproachGoalRequestForNonSpecialChest() {
        SpecialChestTarget.approachGoals(new BlockPos(99, 60, 46));
    }
}
