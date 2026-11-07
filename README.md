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
3. Scan the area around `(47, 66, 75)` for an actual opening in an iron-bar layer, using the block collision snapshot to confirm a player-sized vertical shaft and a safe landing below.
4. Pathfind to that specific opening. The selected A* route must contain a downward DROP transition that starts above the grate and lands below it through the detected aperture.
5. Track the player's feet across the grate height and confirm a downward crossing inside the selected opening. Merely reaching a nearby lower block does not count as Sewer entry.
6. Only after the crossing is verified and the player is grounded below the grate does Geode scan loaded tile entities for existing chests.
7. Pathfind to a standing position beside a chest, open it, shift-click its contents into the player's inventory, then continue scanning.
8. If no chest is found after several scans, patrol nearby waypoints to load more chunks.

The chat message `SEWER! A new treasure spawned somewhere!` is only an optional early scan trigger. Geode also scans periodically, so a chest that existed before the message appeared can still be found.

## Pathfinding

- 3D A* with multiple goal positions.
- Collision snapshot is captured on the client thread; A* runs on a background worker.
- Diagonal corner checks, one-block step-ups, jump arcs, long drops, ladders, and liquid movement.
- Drop movement has **zero extra fall-risk cost**, including straight-down drops through openings. Void/unlandable cells and lava, fire, and cactus are still rejected.
- A stuck movement controller requests a new path.
- A dedicated GatePlanner detects iron-bar frames and validates the full player-sized drop column before a route is accepted.
- Sewer entry requires a detected DROP edge through the chosen opening and a runtime downward-crossing check at the grate plane.
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
- The iron-bar detector searches within three blocks horizontally and two blocks vertically of the supplied approximate gate coordinate. If the real hole is outside that region or the grate is not made of vanilla iron bars, the HUD will report that no framed opening was detected.
- The runtime grate-crossing check and the actual movement through the opening still need validation in the running remake world.
