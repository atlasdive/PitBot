package dev.atlasdive.geode.client;

/**
 * Converts a requested clicks-per-second rate into a 20 Hz client-tick schedule.
 * One call represents one client tick; ineligible ticks discard accumulated
 * credit so attacks are never burst after the crosshair becomes valid again.
 */
public final class CpsAttackClock {
    private int cps;
    private int accumulator;

    public CpsAttackClock(int cps) {
        setCps(cps);
    }

    public void setCps(int cps) {
        if (cps < 1 || cps > 20) {
            throw new IllegalArgumentException("CPS must be between 1 and 20");
        }
        this.cps = cps;
    }

    public int getCps() {
        return cps;
    }

    public boolean tick(boolean eligible) {
        if (!eligible) {
            accumulator = 0;
            return false;
        }
        accumulator += cps;
        if (accumulator >= 20) {
            accumulator -= 20;
            return true;
        }
        return false;
    }

    public void reset() {
        accumulator = 0;
    }
}
