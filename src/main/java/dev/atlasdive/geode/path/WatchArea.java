package dev.atlasdive.geode.path;

/** The rectangular Sewer area where Geode requires exclusive occupancy. */
public final class WatchArea {
    private WatchArea() {}

    public static boolean contains(double x,double feetY,double z){
        return x>=11.0D&&x<105.0D&&feetY>=58.0D&&feetY<62.0D
                &&z>=12.0D&&z<27.0D;
    }
}
