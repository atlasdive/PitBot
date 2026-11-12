package dev.atlasdive.geode.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class RetaliationPolicyTest {
    @Test public void attacksVisibleTargetsWithinMeleeRange() {
        assertTrue(RetaliationPolicy.canAttack(2.0F, true, false));
        assertTrue(RetaliationPolicy.canAttack(3.0F, true, false));
    }

    @Test public void ignoresTargetsOutsideMeleeRangeOrBehindObstacles() {
        assertFalse(RetaliationPolicy.canAttack(3.01F, true, false));
        assertFalse(RetaliationPolicy.canAttack(2.0F, false, false));
    }

    @Test public void doesNotAttackWhileAGuiIsOpenOrAtInvalidDistance() {
        assertFalse(RetaliationPolicy.canAttack(2.0F, true, true));
        assertFalse(RetaliationPolicy.canAttack(-0.1F, true, false));
    }
}
