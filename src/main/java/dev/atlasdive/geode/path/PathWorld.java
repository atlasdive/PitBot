package dev.atlasdive.geode.path;
public interface PathWorld {
    boolean canStand(BlockPos pos);
    boolean clear(BlockPos pos);
    boolean hazard(BlockPos pos);
    boolean climbable(BlockPos pos);
    boolean liquid(BlockPos pos);
    boolean gateBarrier(BlockPos pos);
    double extraCost(BlockPos pos);
    boolean jumpArcClear(BlockPos from,BlockPos to);
    boolean lineClear(BlockPos from,BlockPos to);
    default boolean canStand(PathNode pos){return (pos.y2&1)==0&&canStand(pos.blockPos());}
    default boolean clear(PathNode pos){return clear(pos.blockPos());}
    default boolean hazard(PathNode pos){return hazard(pos.blockPos());}
    default boolean climbable(PathNode pos){return climbable(pos.blockPos());}
    default boolean liquid(PathNode pos){return liquid(pos.blockPos());}
    default double extraCost(PathNode pos){return extraCost(pos.blockPos());}
    default boolean jumpArcClear(PathNode from,PathNode to){return jumpArcClear(from.blockPos(),to.blockPos());}
    default boolean lineClear(PathNode from,PathNode to){
        return from.y2==to.y2&&lineClear(from.blockPos(),to.blockPos());
    }
}
