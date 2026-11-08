package dev.atlasdive.geode.path;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScaffoldArrivalGeometryTest {
    @Test public void waitsAtFinalScaffoldWaypointUntilGrounded() {
        assertTrue(WaypointGeometry.shouldWaitForGroundAtFinalWaypoint(
                true, true, false, 0.20D, 0.10D, 0.52D));
    }

    @Test public void doesNotWaitOncePlayerIsGrounded() {
        assertFalse(WaypointGeometry.shouldWaitForGroundAtFinalWaypoint(
                true, true, true, 0.20D, 0.10D, 0.52D));
    }

    @Test public void onlyWaitsAtTheFinalWaypointOfRoutesThatRequireGround() {
        assertFalse(WaypointGeometry.shouldWaitForGroundAtFinalWaypoint(
                false, true, false, 0.20D, 0.10D, 0.52D));
        assertFalse(WaypointGeometry.shouldWaitForGroundAtFinalWaypoint(
                true, false, false, 0.20D, 0.10D, 0.52D));
    }

    @Test public void doesNotWaitBeforeTheWaypointIsReached() {
        assertFalse(WaypointGeometry.shouldWaitForGroundAtFinalWaypoint(
                true, true, false, 1.00D, 0.10D, 0.52D));
        assertFalse(WaypointGeometry.shouldWaitForGroundAtFinalWaypoint(
                true, true, false, 0.20D, 0.50D, 0.52D));
    }
}
