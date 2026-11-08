package dev.atlasdive.geode.path;

import org.junit.Test;
import static org.junit.Assert.*;

public class SpecialChestTargetTest {
    @Test public void acceptsAllThreeConfiguredVerticalPositions() {
        for (int y = 61; y <= 63; y++) {
            assertTrue("Y=" + y + " should be recognized as the special chest",
                    SpecialChestTarget.matches(new BlockPos(99, y, 46)));
            assertEquals(y - 3.0D,
                    SpecialChestTarget.approachFeetY(new BlockPos(99, y, 46)), 0.0001D);
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
}
