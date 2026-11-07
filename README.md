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
4. Find supported standing cells within four horizontal blocks of each opening, provided there is a clear player-width approach corridor. A* pathfinds to one of those approach cells; it does not require a supported landing directly beneath the hole.
5. From the approach cell, steer into the opening and monitor the player's feet crossing the grate plane downward.
6. Only after the crossing is verified and the player lands below the grate does Geode scan every chest tile entity in client-loaded chunks. There is no horizontal scan-radius or Y-level cutoff and no particle requirement. Every loaded chest is eligible except the explicitly excluded region `x=19..23, y=66..70, z=72..82`.
7. For the elevated chest at `(80, 60, 66)`, pathfind to `(81, 53, 63)`, select solid non-falling blocks from the hotbar, and build seven jump-placed blocks at `(81, 53, 63)` through `(81, 59, 63)`. After landing on the top block at feet position `(81, 60, 63)`, run the normal chest approach pathfinder. If the staging route or block placement fails, restore the selected hotbar slot and pitch, then try normal chest pathfinding.
8. For distant chests, pathfind in A* segments of at most 56 horizontal blocks; once nearby, search standing-position candidates around the chest. Once opened, shift-click one occupied slot every 2 client ticks. Close the GUI only after all chest slots are empty, then continue scanning.
9. If no eligible chest is found after several scans, follow a non-repeating expanding square spiral around the Sewer scan center with no configured distance limit. Long legs are split into 56-block A* segments. Patrol destinations must be at least five blocks from the current player cell, and blocked/unloaded destinations are skipped with a short backoff.

The chat message `SEWER! A new treasure spawned somewhere!` is only an optional early scan trigger. Geode also scans periodically, so a chest that existed before the message appeared can still be found.

## Pathfinding

- 3D A* with multiple goal positions and a default budget of 120,000 expanded nodes.
- Distant chest routes and patrol legs are split into 56-block segments, keeping each collision snapshot bounded without imposing a finite exploration radius.
- Collision snapshot is captured on the client thread with a 24-block horizontal margin around the target region; A* runs on a background worker.
- Diagonal corner checks, one-block step-ups, jump arcs, long drops, ladders, and liquid movement.
- Drop movement has **zero extra fall-risk cost**, including straight-down drops through openings. Void/unlandable cells and lava, fire, and cactus are still rejected.
- A stuck movement controller requests a new path.
- A dedicated GatePlanner detects openings using generic collision barriers and their supported approach cells; it does not require a landing directly underneath the opening.
- The movement controller steps from the chosen approach into the hole and requires a runtime downward-crossing check at the grate plane before Sewer entry.
- When no eligible loaded chest is found, patrol uses an expanding square spiral (8, 8, 16, 16, 24, 24, ... block legs) centered on the Sewer area. The spiral has no configured horizontal radius limit and computes distant waypoints in constant time.
- Patrol skips nearby/current cells and advances past unreachable waypoints without a configured chest scan boundary.
- The HUD displays the detected gap, grate height, crossing verification, patrol leg/waypoint, loaded/excluded chest counts, current state, target chest, and expanded A* node count.
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

- Chest detection is limited to tile entities in chunks currently loaded by the client, but has no configured horizontal distance limit or particle requirement. Geode explores farther areas by travelling its expanding spiral and loading chunks along the way; it cannot inspect unloaded chunks remotely. All loaded chest tile entities are eligible except the region `x=19..23, y=66..70, z=72..82`.
- Shift-clicks are sent at two-client-tick intervals. If an item cannot be moved (for example, because the player's inventory is full), Geode leaves the chest GUI open instead of falsely treating it as emptied.
- The scaffold route is specific to chest `(80, 60, 66)` and requires seven usable solid blocks in the hotbar (multiple stacks are supported). Sand, gravel, liquids, and non-solid items are not selected. The server must allow block placement at the staging column; a rejected placement triggers fallback to the normal pathfinder.
- The chat announcement contains no chest coordinates.
- The gate detector searches within `gateScanRadius` (default 12 blocks) and scans down from the player's current feet layer through the configured maximum fall distance. It recognizes iron bars, panes, fences, walls, and opaque blocks around the opening. If the opening is outside the search region or its surrounding blocks do not create a detectable frame, the HUD reports scan counts.
- The runtime grate-crossing check and the actual movement through the opening still need validation in the running remake world.
