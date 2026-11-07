# Geode

**Geode** is a standalone client-side Minecraft Forge 1.8.9 mod for the Sewer area of the custom Pit remake.

- Forge mod ID: `geode` (lowercase, as required by Forge)
- Display name: `Geode`
- Entry point: `dev.atlasdive.geode.Geode`
- No PitV2 dependency, server plugin, Plugin Message, web API, or external service

## Current route

Press **G** to start or stop Geode.

1. Build a path to the LaunchPad area around `(16, 95, 16)`.
2. Wait for the LaunchPad to launch the player downward. Geode waits for the player to land before planning the next route.
3. Use the 3D pathfinder to reach the narrow target region around the iron-bar gap at `(47, 66, 75)`. The actual collision boxes determine which cells are traversable.
4. Once the player is grounded below the gate area, scan loaded tile entities for existing chests.
5. Pathfind to a standing position beside a chest, open it, shift-click its contents into the player's inventory, then continue scanning.
6. If no chest is found after several scans, patrol nearby waypoints to load more chunks.

The chat message `SEWER! A new treasure spawned somewhere!` is only an optional early scan trigger. Geode also scans periodically, so a chest that existed before the message appeared can still be found.

## Pathfinding

- 3D A* with multiple goal positions.
- Collision snapshot is captured on the client thread; A* runs on a background worker.
- Diagonal corner checks, one-block step-ups, jump arcs, long drops, ladders, and liquid movement.
- Drop movement has **zero extra fall-risk cost**. Void/unlandable cells and lava, fire, and cactus are still rejected.
- A stuck movement controller requests a new path.
- A HUD displays the current state and target chest.

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
- The supplied LaunchPad and iron-bar coordinates are configured, but their exact collision layout and LaunchPad behavior have not yet been tested in the running remake world.
- The ForgeGradle 2.1 build has not been executed in this environment. Build with the specified legacy JDK/Gradle versions, then test first in the private remake server.
