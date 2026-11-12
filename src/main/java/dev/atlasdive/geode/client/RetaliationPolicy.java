package dev.atlasdive.geode.client;

/** Conditions for a non-blocking opportunistic melee counterattack. */
public final class RetaliationPolicy {
    public static final float MELEE_RANGE = 3.0F;

    private RetaliationPolicy() {}

    public static boolean canAttack(float distance, boolean visible, boolean screenOpen) {
        return distance >= 0.0F && distance <= MELEE_RANGE && visible && !screenOpen;
    }
}
