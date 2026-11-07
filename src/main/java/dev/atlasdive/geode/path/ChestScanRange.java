package dev.atlasdive.geode.path;

/** Horizontal circular scan boundary for Sewer chest discovery. */
public final class ChestScanRange {
    private ChestScanRange() {}

    public static boolean contains(int x,int z,int centerX,int centerZ,int radius) {
        if(radius<0)return false;
        long dx=(long)x-centerX,dz=(long)z-centerZ;
        return dx*dx+dz*dz<=(long)radius*radius;
    }
}
