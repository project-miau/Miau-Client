# Baritone for Mimeow 1.8.9

The client includes a source port of [Baritone v1.2.15](https://github.com/cabaletta/baritone/tree/v1.2.15), pinned to commit `861bb7a00a25b1cd08b834d9b52a708689589d35`, for MCP 1.8.9 and the client Java 17 runtime. Upstream source and LGPL notices are retained in this directory.

## Commands

Use `#help` or `.baritone help` in chat. Sending a Baritone command enables the **Baritone** module in **Other**. Turning that module off cancels all processes and releases movement, mining and placement inputs.

| Command | Action |
| --- | --- |
| `#goto 100 64 200` | Navigate to a block position |
| `#goto 100 200` | Navigate to X/Z |
| `#mine 16 diamond_ore` | Mine and collect the requested amount |
| `#follow player PlayerName` | Follow a player |
| `#follow entity pig` | Follow matching entities |
| `#build house.schematic 100 64 200` | Build an MCEdit schematic from an origin |
| `#farm 16` | Harvest and replant nearby crops |
| `#explore` | Explore and cache surrounding terrain |
| `#pause` / `#resume` | Pause or resume the current task |
| `#cancel` | Cancel all tasks |
| `#sel` / `#waypoints` | Selection and waypoint command help |
| `#allowBreak false` | Change a Baritone setting |
| `#set list` | List settings |

Tab completion works for both `#` commands and `.baritone` commands. The `.baritone` command remains available when Baritone's prefix is changed or `prefixControl` is disabled. The existing `.b` alias continues to mean bind.

Put `.schematic` or `.schem` files in `<game directory>/schematics/`. MCEdit files preserve legacy block IDs and metadata. Sponge palette names and state properties are resolved against the 1.8.9 block registry. Building consumes materials from the player's inventory in survival mode.

Settings are saved in `<game directory>/baritone/settings.txt`. Singleplayer world caches and waypoints are stored in `<save>/baritone/DIM<id>/`; multiplayer data uses `<game directory>/baritone/<server>/DIM<id>/`.

## FightBot

**FightBot** is in **Combat**. It selects nearby players, uses a Baritone process to navigate around obstacles, then uses direct movement inputs to maintain combat distance. `Continuous` keeps strafing/advancing; `Rhythm` alternates movement and a short stop, with sprint resets after confirmed hits. Move/stop durations are measured in client ticks.

KillAura owns attack timing, attack packets and combat aim. FightBot respects its player, invisible, friend, bot and teammate filters, and KillAura prioritizes the player FightBot is pursuing when that player is in its valid combat range. `Auto KillAura` enables Aura when FightBot starts and restores that state when FightBot stops; an Aura enabled manually stays enabled.

Opening a screen pauses the bot. Target death, movement and range changes trigger reselection or a new path. `#cancel`, disabling FightBot, or leaving the world releases its inputs and stops its process. Combat navigation disables block breaking/placement in its calculation context without changing global Baritone settings.

## Implementation

`idle.miau.baritone.BaritoneBridge` connects the original processes to the MCP client event bus, player rotation/movement, packets, chunk population, world lifecycle and rendering. The upstream Mixin/Forge bootstrap is replaced by the client integration. Legacy nullable inventory slots, one-hand interaction, chunk storage, block states, potions and enchantments are adapted in the vendored source.

The complete ground movement implementation and rotation processing are imported from **Baritone v1.20.0** (25111daedf1d59e6a8dfb5a3e61885cdb8d953df). All eight movement classes, move selection, costs, collision/passability caching, calculation context and PathExecutor use that source, with the 1.8.9 bindings recorded in [UPSTREAM.md](UPSTREAM.md). The command/process and A* infrastructure remains on the adapted v1.2.15 baseline and consumes the new movement implementation.

Navigation submits upstream aim to the client rotation arbiter. KillAura keeps combat priority; the client adapter converts inputs between navigation and combat angles and preserves normal input events. Upstream look settings include randomLooking, randomLooking113, blockFreeLook, smoothLook, smoothLookTicks and remainWithExistingLookDirection. Custom turn limits and target prediction are removed.

The ZKM configurations preserve Baritone setting fields, command names and generic signatures used by reflection. The upstream API is available through `baritone.api.BaritoneAPI`.

## Verification

```powershell
.\gradlew.bat --offline test
.\gradlew.bat --offline baritoneSourceGameplaySmoke
.\gradlew.bat --offline fightBotGameplaySmoke
.\gradlew.bat --offline baritoneMovementGameplaySmoke
```

The gameplay smoke test launches a hidden client window and an isolated integrated-server world under `build/baritone/gameplay/`. It checks command registration/completion, routing around a wall, pause/resume, mining and collecting a diamond, schematic placement, following an entity, harvesting and replanting wheat, exploration, canceling, module disable, container memory, bed/death waypoints and world unload. Its report is `build/baritone/gameplay/gameplay.txt`. It does not use existing saves or client configuration.

The source gameplay tasks run current class files and resources without packaging the client JAR. They reuse the hidden-window verification agent at `build/baritone/gameplay-agent.jar`. FightBot results are saved to `build/fightbot/gameplay/gameplay.txt`.

Source gameplay fixtures pre-load movement classes and use upstream search/drop-wait settings with longer deadlines to tolerate class loading and an already-running client. Production defaults are unchanged; the fixtures do not benchmark those default deadlines.

The movement course verifies native path selection and server-confirmed traversal, diagonal travel, ascent/descent, falling, flat/upward parkour, bridge and pillar placement, downward mining, ladders/vines, water landing, water columns, slab transitions and opening doors. Its report is build/baritone-movement/gameplay/gameplay.txt.

[Movement source manifest](MOVEMENT-UPSTREAM.json) records the pinned source and native bindings. Run python verification/check_baritone_movement_sources.py to verify the local import, optionally adding --upstream-root with a checkout of the pinned tag to verify both sides.
