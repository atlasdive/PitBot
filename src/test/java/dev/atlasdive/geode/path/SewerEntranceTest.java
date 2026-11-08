package dev.atlasdive.geode.path;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SewerEntranceTest {
    @Test public void registersBothAdditionalEntrancesAndTheirInteriorDestinations() {
        assertEquals(2, SewerEntrance.all().size());

        SewerEntrance first = SewerEntrance.all().get(0);
        assertEquals(new BlockPos(116, 68, 28), first.getStart());
        assertEquals(new BlockPos(112, 59, 30), first.getDestination());
        assertTrue("the first route is explicitly allowed to pass through water",
                first.passesThroughWater());

        SewerEntrance second = SewerEntrance.all().get(1);
        assertEquals(new BlockPos(106, 66, 54), second.getStart());
        assertEquals(new BlockPos(94, 58, 56), second.getDestination());
        assertFalse(second.passesThroughWater());
    }

    @Test public void resolvesAnEntranceFromItsUpperEndpoint() {
        for (SewerEntrance entrance : SewerEntrance.all()) {
            assertSame(entrance, SewerEntrance.byStart(entrance.getStart()));
        }
        assertNull(SewerEntrance.byStart(new BlockPos(47, 66, 75)));
    }

    @Test public void destinationRetryCandidatesStayAroundTheSelectedInteriorPoint() {
        SewerEntrance entrance = SewerEntrance.byStart(new BlockPos(106, 66, 54));
        assertNotNull(entrance);

        assertEquals(java.util.Collections.singletonList(new BlockPos(94, 58, 56)),
                entrance.destinationCandidates(0));

        List<BlockPos> nearby = entrance.destinationCandidates(1);
        assertTrue(nearby.contains(new BlockPos(94, 58, 56)));
        assertTrue(nearby.contains(new BlockPos(93, 58, 56)));
        assertTrue(nearby.contains(new BlockPos(95, 59, 56)));
        for (BlockPos candidate : nearby) {
            assertTrue(Math.abs(candidate.x - 94) <= 1);
            assertTrue(Math.abs(candidate.y - 58) <= 1);
            assertTrue(Math.abs(candidate.z - 56) <= 1);
        }
    }

    @Test public void routeIdsAreUnique() {
        Set<String> ids = new HashSet<String>();
        for (SewerEntrance entrance : SewerEntrance.all()) {
            assertTrue(ids.add(entrance.getId()));
        }
    }
}
