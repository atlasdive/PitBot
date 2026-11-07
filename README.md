# Geode

**Geode** is a standalone client-side Minecraft Forge 1.8.9 mod for the Sewer area of the custom Pit remake.

- Forge mod ID: `geode` (lowercase, as required by Forge)
- Display name: `Geode`
- Entry point: `dev.atlasdive.geode.Geode`
- No PitV2 dependency, server plugin, Plugin Message, web API, or external service

## Current route

Press **G** to start or stop Geode.

1. Prioritize the LaunchPad column at `(16, 95, 16)`; only use nearby safe cells if the exact column cannot be occupied.
2. Wait for the LaunchPad to launch the player downward. Geode waits for the player to land before planning the next route.
3. Scan within the configurable gate radius (default 12 blocks) around `(47, 66, 75)` for an opening in a framed floor/railing structure. Detection recognizes iron bars, panes, fences, walls, and opaque solid frame blocks rather than relying on one exact block ID.
4. Find supported standing cells beside each opening. A* pathfinds to one of those approach cells; it does not require a supported landing directly beneath the hole.
5. From the approach cell, steer into the opening and monitor the player's feet crossing the grate plane downward.
6. Only after the crossing is verified and the player lands below the grate does Geode scan loaded tile entities for existing chests.
7. Pathfind to a standing position beside a chest, open it, shift-click its contents into the player's inventory, then continue scanning.
8. If no chest is found after several scans, patrol nearby waypoints to load more chunks.

The chat message `SEWER! A new treasure spawned somewhere!` is only an optional early scan trigger. Geode also scans periodically, so a chest that existed before the message appeared can still be found.

## Pathfinding

- 3D A* with multiple goal positions.
- Collision snapshot is captured on the client thread; A* runs on a background worker.
- Diagonal corner checks, one-block step-ups, jump arcs, long drops, ladders, and liquid movement.
- Drop movement has **zero extra fall-risk cost**, including straight-down drops through openings. Void/unlandable cells and lava, fire, and cactus are still rejected.
- A stuck movement controller requests a new path.
- A dedicated GatePlanner detects openings using generic collision barriers and their supported approach cells; it does not require a landing directly underneath the opening.
- The movement controller steps from the chosen approach into the hole and requires a runtime downward-crossing check at the grate plane before Sewer entry.
- The HUD displays the detected gap, grate height, crossing verification, current state, target chest, and expanded A* node count.
- The active route is drawn in-world: cyan for normal movement, yellow for jumps/steps, and orange for drops.

## Server allowlist

Geode is intended for the owner's remake server. Singleplayer is allowed. Multiplayer requires the exact hostname in `config/geode.cfg` under `security.allowedServers`.

The defaults are `localhost` and `127.0.0.1`. Add the hostname of the private remake server before starting Geode. An unlisted multiplayer host is blocked.

## Build

Use **JDK 8**, **Gradle 2.14.1**, and **ForgeGradle 2.1** for Minecraft Forge 1.8.9.

```bash
gradle setupDecompWorkspace
gradle build
```

The JAR should be written to `build/libs/Geode-0.1.0.jar`.

## Important limitations

- Chest detection is limited to tile entities in chunks currently loaded by the client. Geode patrols nearby waypoints to load more chunks; it cannot inspect unloaded chunks remotely.
- The chat announcement contains no chest coordinates.
- The gate detector searches within `gateScanRadius` (default 12 blocks) and scans down from the player's current feet layer through the configured maximum fall distance. It recognizes iron bars, panes, fences, walls, and opaque blocks around the opening. If the opening is outside the search region or its surrounding blocks do not create a detectable frame, the HUD reports scan counts.
- The runtime grate-crossing check and the actual movement through the opening still need validation in the running remake world.
