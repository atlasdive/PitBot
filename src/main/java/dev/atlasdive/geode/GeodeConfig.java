package dev.atlasdive.geode;

import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.Locale;

public final class GeodeConfig {
    private static Configuration config;
    public static String[] allowedServers = {"localhost", "127.0.0.1"};
    public static int scanIntervalTicks = 20;
    public static int particleProximityRadius = 3;
    public static int particleMemoryTicks = 100;
    public static int maxExpandedNodes = 120000;
    public static int maxFallBlocks = 48;
    public static int gateScanRadius = 12;

    private GeodeConfig() {}

    public static void load(File file) {
        config = new Configuration(file);
        allowedServers = config.getStringList("allowedServers", "security", allowedServers,
                "Exact hostnames where Geode may run. Add your own remake server.");
        scanIntervalTicks = config.getInt("scanIntervalTicks", "chests", 20, 5, 100,
                "Ticks between scans of loaded chunks.");
        particleProximityRadius = config.getInt("particleProximityRadius", "chests", 3, 1, 8,
                "Maximum distance in blocks from a chest to a particle position.");
        particleMemoryTicks = config.getInt("particleMemoryTicks", "chests", 100, 20, 300,
                "How long particle positions remain valid (20 ticks = 1 second).");
        // Remove the retired finite-distance scan settings. Discovery now has no
        // horizontal radius; only chunks loaded by the client can be inspected.
        if (config.hasKey("chests", "scanRadius")) config.getCategory("chests").remove("scanRadius");
        if (config.hasKey("chests", "scanRadiusMigration")) config.getCategory("chests").remove("scanRadiusMigration");
        maxExpandedNodes = config.getInt("maxExpandedNodes", "pathfinding", 120000, 5000, 250000,
                "Maximum A* nodes expanded in one request.");
        int pathBudgetMigration = config.getInt("pathBudgetMigration", "pathfinding", 0, 0, 2,
                "Internal config migration marker.");
        if (pathBudgetMigration < 2) {
            if (maxExpandedNodes == 45000 || maxExpandedNodes == 75000) {
                maxExpandedNodes = 120000;
                config.get("pathfinding", "maxExpandedNodes", 120000).set(120000);
            }
            config.get("pathfinding", "pathBudgetMigration", 2).set(2);
        }
        maxFallBlocks = config.getInt("maxFallBlocks", "pathfinding", 48, 1, 96,
                "Maximum vertical drop considered. Falls have no extra damage penalty.");
        gateScanRadius = config.getInt("gateScanRadius", "pathfinding", 12, 4, 24,
                "Horizontal search radius around the approximate Sewer gate marker.");
        if (config.hasChanged()) config.save();
    }

    public static boolean isAllowed(String address, boolean singleplayer) {
        if (singleplayer) return true;
        if (address == null || address.trim().isEmpty()) return false;
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        for (String value : allowedServers) {
            if (value != null && host.equals(value.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
