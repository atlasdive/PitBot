package dev.atlasdive.geode.path;

/** Retry a failed chest from a new position instead of hiding it for its full cooldown. */
public final class ChestCooldownPolicy {
    private ChestCooldownPolicy() {}

    public static boolean shouldSuppress(long now,long until,BlockPos retryOrigin,
                                          BlockPos currentPosition,double movedDistance) {
        if(until<=now)return false;
        if(retryOrigin==null||currentPosition==null)return true;
        if(movedDistance<0.0D)return true;
        double dx=(double)currentPosition.x-retryOrigin.x;
        double dz=(double)currentPosition.z-retryOrigin.z;
        return dx*dx+dz*dz<movedDistance*movedDistance;
    }
}
