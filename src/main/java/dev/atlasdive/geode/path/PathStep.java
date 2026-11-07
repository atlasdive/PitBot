package dev.atlasdive.geode.path;
public final class PathStep {
    public final BlockPos pos;
    public final PathNode node;
    public final double feetY;
    public final MovementKind kind;
    public final double cost;
    public PathStep(BlockPos pos,MovementKind kind,double cost){this(PathNode.fromBlockPos(pos),kind,cost);}
    public PathStep(PathNode node,MovementKind kind,double cost){
        this.node=node;this.pos=node.blockPos();this.feetY=node.feetY();this.kind=kind;this.cost=cost;
    }
}
