package dev.atlasdive.geode.path;

import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.Assert.*;

public class SpecialChestRoutePlannerTest {
    @Test public void exposesAllThirteenExactCoordinatesWithoutDuplicates(){
        List<BlockPos> points=SpecialChestRoutePlanner.checkpoints();
        assertEquals(13,points.size());
        assertEquals(13,new HashSet<BlockPos>(points).size());
        assertFalse(points.contains(SpecialChestRoutePlanner.finalTarget()));
        assertTrue(points.contains(new BlockPos(47,51,70)));
        assertTrue(points.contains(new BlockPos(69,53,72)));
        assertTrue(points.contains(new BlockPos(78,53,58)));
        assertTrue(points.contains(new BlockPos(64,55,59)));
        assertTrue(points.contains(new BlockPos(47,52,55)));
        assertTrue(points.contains(new BlockPos(83,58,55)));
        assertTrue(points.contains(new BlockPos(104,58,50)));
        assertTrue(points.contains(new BlockPos(107,58,39)));
        assertTrue(points.contains(new BlockPos(93,58,32)));
        assertTrue(points.contains(new BlockPos(89,58,37)));
        assertTrue(points.contains(new BlockPos(83,57,24)));
        assertTrue(points.contains(new BlockPos(66,54,17)));
        assertTrue(points.contains(new BlockPos(103,58,12)));
    }

    @Test public void switchesToFinalApproachWithinTheExistingChestSegmentRange(){
        assertTrue(SpecialChestRoutePlanner.isWithinFinalApproachRange(75.5D,46.5D,24));
        assertTrue(SpecialChestRoutePlanner.isWithinFinalApproachRange(99.5D,46.5D,24));
        assertFalse(SpecialChestRoutePlanner.isWithinFinalApproachRange(74.4D,46.5D,24));
    }

    @Test public void remainingRemovesVisitedCoordinatesOnly(){
        Set<BlockPos> visited=new HashSet<BlockPos>();
        visited.add(new BlockPos(69,53,72));
        visited.add(new BlockPos(83,58,55));
        List<BlockPos> remaining=SpecialChestRoutePlanner.remaining(visited);
        assertEquals(11,remaining.size());
        assertFalse(remaining.contains(new BlockPos(69,53,72)));
        assertTrue(remaining.contains(new BlockPos(78,53,58)));
    }

    @Test public void endpointMustMatchTheExactConfiguredBlock(){
        List<BlockPos> candidates=SpecialChestRoutePlanner.checkpoints();
        assertEquals(new BlockPos(93,58,32),
                SpecialChestRoutePlanner.matchCheckpoint(new BlockPos(93,58,32),candidates));
        assertNull(SpecialChestRoutePlanner.matchCheckpoint(new BlockPos(93,57,32),candidates));
    }
}
