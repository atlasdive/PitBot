# Geode

Standalone client-side Minecraft Forge 1.8.9 mod for the custom Pit Sewer. Forge mod ID: `geode`; display name: `Geode`.

Geode is independent from PitV2. It does not use server plugins, Plugin Messages, web APIs, or external services.

## Route and behavior

1. Press **G** to start or stop.
2. Pathfind to the LaunchPad near `(16, 95, 16)`.
3. Wait for the LaunchPad's downward launch. If it does not activate within about ten seconds, attempt the Sewer route directly.
4. Pathfind toward a standable position below the iron-bar opening near `(47, 66, 75)`. Collision checks use the player's dimensions. Clear drops have no extra fall-risk cost; void and damaging blocks remain invalid.
5. Scan loaded chunk tile entities for Sewer chests on entry and periodically. The scan does not depend on the `SEWER! A new treasure spawned somewhere!` message, so already-existing chests can still be found.
6. Pathfind to a neighboring standing position, open the chest, shift-click its contents into the player's inventory, then continue searching. If no loaded chest is found after several scans, patrol nearby waypoints to load more chunks.

## Server allowlist

Geode is intended for the owner's remake server. Singleplayer is allowed. Multiplayer requires the exact hostname in `config/geode.cfg` under `security.allowedServers`. Defaults are `localhost` and `127.0.0.1`. Add the private server hostname before starting Geode. The mod stops on an unlisted host.

## Build

Requires JDK 8, Gradle 2.14.1, and ForgeGradle 2.1 for Minecraft Forge 1.8.9.

```bash
gradle setupDecompWorkspace
gradle build
```

Output: `build/libs/Geode-0.1.0.jar`.

## Limitations

- Detection is limited to tile entities in client-loaded chunks. Patrol helps load nearby chunks, but cannot inspect unloaded chunks remotely.
- The spawn chat message is only an early scan trigger; it contains no chest coordinates.
- The supplied LaunchPad and iron-bar coordinates are wired in, but their real collision layout must be tested in the running remake world. The pathfinder has not been validated against the unseen map yet.
