# Baritone for Mimeow 1.8.9

Upstream: https://github.com/cabaletta/baritone

Movement and rotation source: **v1.20.0**, commit
25111daedf1d59e6a8dfb5a3e61885cdb8d953df.
Release: https://github.com/cabaletta/baritone/releases/tag/v1.20.0

The complete ground movement implementation is imported from this tag:

- All eight movement classes: Traverse, Diagonal, Ascend, Descend, Downward,
  Fall, Parkour and Pillar.
- Movement, MovementState, Moves and MovementOption.
- CalculationContext, including the current placement/breaking rules.
- MovementHelper and PrecomputedData/Ternary collision/passability caching.
- PathExecutor, ActionCosts, IMovement and its Pair utility dependency.

These are whole upstream sources with version bindings, replacing the previous
selected movement backports. Parkour candidate selection, falls and bucket
handling, diagonal cancellation, bridge placement, pillar execution, sprint
decisions and movement costs now use the v1.20.0 implementation.

The previously imported v1.20.0 LookBehavior, aim processor API, ForkableRandom,
Rotation and RotationUtils remain in use. The client does not add its former
45/30-degree navigation limits or two-tick target prediction.

## Minecraft 1.8.9 bindings

MovementPlatform189 contains the version-specific state/physics conversions:

- Mojang names map to MCP names; inventory, packet and bounding-box APIs use
  their 1.8 counterparts. Player input retains the native client pre/post events.
- Fluid levels use legacy block metadata. Falling water has full fluid amount
  and is distinct from a source block. Modern water efficiency maps to Depth Strider.
- Block-shape checks use native 1.8 collision boxes and piston extension state.
  Double slabs and half slabs use their actual separate legacy block encodings.
- Door properties are merged from both halves before testing passage.
- 1.8 ladders/vines require movement into their support block. The bridge supplies
  that horizontal target in place of modern jump-to-climb.
- World bounds are 0..255, and the state cache uses the 16-bit block/metadata ID.
  Elytra, Frost Walker, waterlogging and later block families do not exist in 1.8.9;
  their branches cannot be enabled by this port.

The client event bridge replaces upstream Mixins. It submits the upstream
processor's angles to the existing rotation arbiter without re-quantizing them.
MovementFrame only converts navigation inputs into the rotation owned by combat.
FightBot's requested rhythm and KillAura coordination remain client features.
Its calculation context disables breaking/placement, including allowBreakAnyway,
without changing global settings.

The command/process, cache and A* infrastructure remains on the adapted
**v1.2.15** baseline, commit 861bb7a00a25b1cd08b834d9b52a708689589d35.
A* now expands the v1.20.0 Moves and consumes its movement costs. This update
replaces the movement mechanism; it does not claim a whole-engine v1.20.0 port.

The source targets Java 17 and MCP 1.8.9. Original LGPL notices and the license
are retained. Verification runs from current classes without packaging a client JAR.
