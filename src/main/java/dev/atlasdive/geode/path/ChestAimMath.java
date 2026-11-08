package dev.atlasdive.geode.path;

/** Converts a world-space target point to Minecraft yaw and pitch. */
public final class ChestAimMath {
    private ChestAimMath() {}

    public static float yaw(double playerX,double playerZ,double targetX,double targetZ){
        return (float)(Math.toDegrees(Math.atan2(targetZ-playerZ,targetX-playerX))-90.0D);
    }

    public static float pitch(double playerX,double eyeY,double playerZ,
                              double targetX,double targetY,double targetZ){
        double dx=targetX-playerX,dz=targetZ-playerZ;
        double horizontal=Math.sqrt(dx*dx+dz*dz);
        return (float)-Math.toDegrees(Math.atan2(targetY-eyeY,horizontal));
    }
}
