package dev.atlasdive.geode.client;

/** Detects the local player's client-side hurt animation without relying on server-only damage hooks. */
public final class HurtTimeDetector {
    private int previousHurtTime=-1;

    /** Returns true only when hurtTime rises, which indicates a new hurt animation. */
    public boolean observe(int currentHurtTime){
        boolean increased=previousHurtTime>=0&&currentHurtTime>previousHurtTime;
        previousHurtTime=currentHurtTime;
        return increased;
    }

    public void reset(){
        previousHurtTime=-1;
    }
}