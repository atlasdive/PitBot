package dev.atlasdive.geode.path;
public final class PathStep {
    public final BlockPos pos;
    public final PathNode node;
    public final double feetY,targetX,targetZ;
    public final MovementKind kind;
    public final double cost;
    /** Requires precise arrival at the centre of a stair/slab support cell. */
    public final boolean centerOnly;
    public PathStep(BlockPos pos,MovementKind kind,double cost){this(PathNode.fromBlockPos(pos),kind,cost,false);}
    public PathStep(PathNode node,MovementKind kind,double cost){this(node,kind,cost,false);}
    public PathStep(PathNode node,MovementKind kind,double cost,boolean centerOnly){
        this.node=node;this.pos=node.blockPos();this.feetY=node.feetY();
        this.targetX=node.worldX();this.targetZ=node.worldZ();
        this.kind=kind;this.cost=cost;this.centerOnly=centerOnly;
    }
}
