package dev.atlasdive.geode.client;

import org.junit.Test;
import static org.junit.Assert.*;

public class HurtTimeDetectorTest {
    @Test public void ignoresInitialValueAndCountdown(){
        HurtTimeDetector detector=new HurtTimeDetector();
        assertFalse(detector.observe(10));
        assertFalse(detector.observe(9));
        assertFalse(detector.observe(5));
        assertFalse(detector.observe(0));
    }

    @Test public void detectsHurtTimeRisingAfterAHit(){
        HurtTimeDetector detector=new HurtTimeDetector();
        assertFalse(detector.observe(0));
        assertTrue(detector.observe(10));
        assertFalse(detector.observe(9));
        assertTrue(detector.observe(10));
    }

    @Test public void resetRequiresANewBaseline(){
        HurtTimeDetector detector=new HurtTimeDetector();
        detector.observe(4);
        detector.reset();
        assertFalse(detector.observe(10));
        assertTrue(detector.observe(9+1));
    }
}