package dev.atlasdive.geode.path;

/** Matching and approach geometry for the elevated Sewer special chest. */
public final class SpecialChestTarget {
    private static final int X = 99;
    private static final int Z = 46;
    private static final int MIN_Y = 61;
    private static final int MAX_Y = 63;

    private SpecialChestTarget() {}

    /** Accept the special chest at any of the configured vertical block positions. */
    public static boolean matches(BlockPos chest) {
        return chest != null && chest.x == X && chest.z == Z
                && chest.y >= MIN_Y && chest.y <= MAX_Y;
    }

    /** Keep the same three-block vertical offset used by the original Y=61 approach. */
    public static double approachFeetY(BlockPos chest) {
        if (!matches(chest)) throw new IllegalArgumentException("not a special chest target");
        return chest.y - 3.0D;
    }
}
