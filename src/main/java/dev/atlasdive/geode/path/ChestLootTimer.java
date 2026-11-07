package dev.atlasdive.geode.path;

/** Enforces a minimum two-client-tick delay between chest shift-clicks. */
public final class ChestLootTimer {
    private int ticks;

    public boolean isReady() {
        if(++ticks<2)return false;
        ticks=0;
        return true;
    }

    public void reset() {
        ticks=0;
    }
}
