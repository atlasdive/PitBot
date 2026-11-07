package dev.atlasdive.geode;

import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.util.Locale;

public final class GeodeConfig {
    private static Configuration config;
    public static String[] allowedServers = {"localhost", "127.0.0.1"};
    public static int scanIntervalTicks = 20;
    public static int scanRadius = 150;
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
        scanRadius = config.getInt("scanRadius", "chests", 150, 8, 256,
                "Horizontal Sewer chest scan radius in blocks.");
        // Migrate previous generated defaults (32/64) to 150 while preserving
        // values that the user deliberately customized.
        int scanRadiusMigration = config.getInt("scanRadiusMigration", "chests", 0, 0, 2,
                "Internal config migration marker.");
        if (scanRadiusMigration < 2) {
            if (scanRadius == 32 || scanRadius == 64) {
                scanRadius = 150;
                config.get("chests", "scanRadius", 150).set(150);
            }
            config.get("chests", "scanRadiusMigration", 2).set(2);
        }
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
