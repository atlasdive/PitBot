package dev.atlasdive.geode.path;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    @Test public void exposesNearbyApproachCellsAndAllLanesWithoutUsingTheChestBlock() {
        for (int y = 61; y <= 63; y++) {
            BlockPos chest = new BlockPos(99, y, 46);
            List<PathNode> goals = SpecialChestTarget.approachGoals(chest);
            assertEquals(40, goals.size());
            Set<String> unique = new HashSet<String>();
            for (PathNode goal : goals) {
                assertEquals((y - 3) * 2, goal.y2);
                assertTrue(goal.x >= 98 && goal.x <= 100);
                assertTrue(goal.z >= 46 && goal.z <= 48);
                assertFalse("the occupied chest block must not be a standing goal",
                        goal.x == chest.x && goal.z == chest.z);
                unique.add(goal.x + ":" + goal.y2 + ":" + goal.z + ":" + goal.lane);
            }
            assertEquals("every approach node must be unique", goals.size(), unique.size());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsApproachGoalRequestForNonSpecialChest() {
        SpecialChestTarget.approachGoals(new BlockPos(99, 60, 46));
    }
}
