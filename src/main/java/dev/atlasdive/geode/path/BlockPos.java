package dev.atlasdive.geode.path;

public final class BlockPos {
    public final int x, y, z;
    public BlockPos(int x, int y, int z) { this.x=x; this.y=y; this.z=z; }
    public BlockPos add(int dx, int dy, int dz) { return new BlockPos(x+dx,y+dy,z+dz); }
    public double distanceSquared(double px, double py, double pz) {
        double dx=x+0.5D-px, dy=y-py, dz=z+0.5D-pz;
        return dx*dx+dy*dy+dz*dz;
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof BlockPos)) return false;
        BlockPos p=(BlockPos)o; return x==p.x && y==p.y && z==p.z;
    }
    @Override public int hashCode() { return ((x*31)+y)*31+z; }
    @Override public String toString() { return "(" + x + ", " + y + ", " + z + ")"; }
}
