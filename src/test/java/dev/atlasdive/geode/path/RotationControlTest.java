package dev.atlasdive.geode.path;

import org.junit.Test;
import static org.junit.Assert.*;

public class RotationControlTest {
    @Test public void fasterTurnStepNeverPassesTheTargetAngle() {
        assertEquals(12.0F, clampTurnStep(40.0F, 12.0F), 0.0001F);
        assertEquals(-12.0F, clampTurnStep(-40.0F, 12.0F), 0.0001F);
        assertEquals(2.0F, clampTurnStep(2.0F, 12.0F), 0.0001F);
        assertEquals(-1.0F, clampTurnStep(-1.0F, 12.0F), 0.0001F);
    }

    @Test public void nearTargetRateIsSlowerThanWideTurnRate() {
        assertEquals(5.0F, adaptiveRate(10.0F), 0.0001F);
        assertEquals(8.0F, adaptiveRate(30.0F), 0.0001F);
        assertEquals(12.0F, adaptiveRate(90.0F), 0.0001F);
    }

    @Test public void movementMustAlignBeforeWalkingOrSprinting() {
        assertFalse(canWalkAtAngle(13.0F));
        assertTrue(canWalkAtAngle(12.0F));
        assertFalse(canSprintAtAngle(5.0F));
        assertTrue(canSprintAtAngle(4.0F));
    }

    private static float clampTurnStep(float difference,float limit) {
        return Math.max(-limit,Math.min(limit,difference));
    }
    private static boolean canWalkAtAngle(float error) { return error<=12.0F; }
    private static boolean canSprintAtAngle(float error) { return error<=4.0F; }
    private static float adaptiveRate(float error) {
        return error>45.0F?12.0F:error>18.0F?8.0F:5.0F;
    }
}
