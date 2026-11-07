package dev.atlasdive.geode.path;

public interface PathWorld {
    boolean canStand(BlockPos pos);
    boolean clear(BlockPos pos);
    boolean hazard(BlockPos pos);
    boolean climbable(BlockPos pos);
    boolean liquid(BlockPos pos);
    double extraCost(BlockPos pos);
    boolean jumpArcClear(BlockPos from, BlockPos to);

    /** True only when the player's full standing footprint can travel horizontally between two same-level nodes. */
    boolean lineClear(BlockPos from, BlockPos to);
}
