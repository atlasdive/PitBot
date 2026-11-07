package dev.atlasdive.geode.path;

/** Fixed scaffold route for the elevated Sewer chest at (80, 60, 66). */
public final class ChestScaffoldPlan {
    private static final BlockPos CHEST=new BlockPos(80,60,66);
    private static final BlockPos STAGING_FEET=new BlockPos(81,53,63);
    private static final int PLACEMENTS=7;

    private ChestScaffoldPlan() {}

    public static ChestScaffoldPlan forChest(BlockPos chest) {
        return CHEST.equals(chest)?new ChestScaffoldPlan():null;
    }

    public BlockPos getStagingFeet() { return STAGING_FEET; }
    public int getPlacementCount() { return PLACEMENTS; }

    /** Block cells are (81,53,63) through (81,59,63), inclusive. */
    public BlockPos getPlacedBlock(int index) {
        if(index<0||index>=PLACEMENTS)throw new IndexOutOfBoundsException("scaffold index: "+index);
        return STAGING_FEET.add(0,index,0);
    }

    public BlockPos getSupportBlock(int index) { return getPlacedBlock(index).add(0,-1,0); }

    /** Feet position after landing on the seventh and final scaffold block. */
    public BlockPos getTopStandingPosition() { return STAGING_FEET.add(0,PLACEMENTS,0); }
}
