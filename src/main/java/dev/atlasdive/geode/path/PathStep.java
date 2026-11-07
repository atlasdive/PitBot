package dev.atlasdive.geode.path;
public final class PathStep {
    public final BlockPos pos;
    public final MovementKind kind;
    public final double cost;
    public PathStep(BlockPos pos, MovementKind kind, double cost) {
        this.pos=pos; this.kind=kind; this.cost=cost;
    }
}
