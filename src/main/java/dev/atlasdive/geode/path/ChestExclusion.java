package dev.atlasdive.geode.path;

/** Coordinates of the only chest region that Geode must ignore. */
public final class ChestExclusion {
    private ChestExclusion() {}

    public static boolean isExcluded(int x,int y,int z) {
        return x>=19&&x<=23&&y>=66&&y<=70&&z>=72&&z<=82;
    }
}
