package dev.atlasdive.geode.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class CpsAttackClockTest {
    @Test public void emitsRequestedAttackCountAcrossOneSecond() {
        for (int cps = 16; cps <= 18; cps++) {
            CpsAttackClock clock = new CpsAttackClock(cps);
            int attacks = 0;
            for (int tick = 0; tick < 20; tick++) {
                if (clock.tick(true)) attacks++;
            }
            assertEquals(cps, attacks);
        }
    }

    @Test public void doesNotBuildAttackCreditWhileCrosshairIsNotOnTarget() {
        CpsAttackClock clock = new CpsAttackClock(18);
        for (int tick = 0; tick < 10; tick++) {
            assertFalse(clock.tick(false));
        }
        assertFalse(clock.tick(true));
        assertTrue(clock.tick(true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRatesAboveTheClientTickLimit() {
        new CpsAttackClock(21);
    }
}
