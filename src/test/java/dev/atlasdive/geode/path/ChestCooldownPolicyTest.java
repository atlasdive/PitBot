package dev.atlasdive.geode.path;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChestCooldownPolicyTest {
    @Test public void failedChestRemainsSuppressedAtSamePosition() {
        assertTrue(ChestCooldownPolicy.shouldSuppress(
                100L,1200L,new BlockPos(108,58,29),new BlockPos(108,58,29),8.0D));
    }

    @Test public void failedChestCanBeRetriedAfterPlayerMovesAway() {
        assertFalse(ChestCooldownPolicy.shouldSuppress(
                100L,1200L,new BlockPos(108,58,29),new BlockPos(116,58,29),8.0D));
    }

    @Test public void successfulChestCooldownRemainsUntilExpiry() {
        assertTrue(ChestCooldownPolicy.shouldSuppress(
                100L,1200L,null,new BlockPos(200,58,200),8.0D));
        assertFalse(ChestCooldownPolicy.shouldSuppress(
                1200L,1200L,null,new BlockPos(200,58,200),8.0D));
    }
}
