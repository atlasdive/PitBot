package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class SewerChestLocationsTest {
    @Test
    public void registeredCoordinatesAreConvertedToBlockCells() {
        List<BlockPos> positions = SewerChestLocations.registered();
        assertEquals(11, positions.size());
        assertTrue(positions.contains(new BlockPos(89, 58, 13)));
        assertTrue(positions.contains(new BlockPos(47, 53, 85)));
        assertTrue(positions.contains(new BlockPos(106, 60, 14)));
        assertTrue(positions.contains(new BlockPos(82, 58, 41)));
    }

    @Test
    public void allTargetsIncludeExistingSpecialLevelsWithoutDuplicates() {
        List<BlockPos> targets = SewerChestLocations.allTargets();
        assertEquals(14, targets.size());
        assertEquals(targets.size(), new HashSet<BlockPos>(targets).size());
        assertTrue(targets.contains(new BlockPos(80, 60, 66)));
        assertTrue(targets.contains(new BlockPos(99, 61, 46)));
        assertTrue(targets.contains(new BlockPos(99, 62, 46)));
        assertTrue(targets.contains(new BlockPos(99, 63, 46)));
    }
}
