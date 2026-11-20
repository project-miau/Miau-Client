package idle.miau.module.impl.movement.noslow;

import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public final class GrimBlink {
    public static final long DEFAULT_MIN_COOLDOWN_MS = 400L;
    public static final long DEFAULT_MAX_COOLDOWN_RANDOM_MS = 200L;
    public static final int DEFAULT_MIN_SWAP_DELAY_TICKS = 2;
    public static final int DEFAULT_MAX_SWAP_DELAY_RANDOM_TICKS = 3;

    private final Consumer<Packet<?>> sender;
    private final LongSupplier timeSupplier;
    private final Random random;

    private int noSlowTick;
    private int swapDelay;
    private int swappedSlot = -1;
    private int lastSlot = -1;
    private long lastSwapTime;
    private long currentCooldown = DEFAULT_MIN_COOLDOWN_MS;

    private int ticksSinceLastAttack = 100;
    private int ticksSinceLastRightClick = 100;
    private int ticksSinceLastRelease = 100;
    private boolean pendingRestore = false;
    private boolean hasBypassed = false;
    private boolean combatActive = false;

    public GrimBlink(Consumer<Packet<?>> sender) {
        this(sender, System::currentTimeMillis, new Random());
    }

    public GrimBlink(Consumer<Packet<?>> sender, LongSupplier timeSupplier, Random random) {
        this.sender = sender;
        this.timeSupplier = timeSupplier;
        this.random = random;
    }

    public void onPacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) packet;
            if (use.getAction() == C02PacketUseEntity.Action.ATTACK) {
                ticksSinceLastAttack = 0;
            } else if (use.getAction() == C02PacketUseEntity.Action.INTERACT
                    || use.getAction() == C02PacketUseEntity.Action.INTERACT_AT) {
                ticksSinceLastRightClick = 0;
            } else {
                ticksSinceLastAttack = 0;
            }
        } else if (packet instanceof C08PacketPlayerBlockPlacement) {
            ticksSinceLastRightClick = 0;
        } else if (packet instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging dig = (C07PacketPlayerDigging) packet;
            if (dig.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                ticksSinceLastRelease = 0;
            }
        }
    }

    public boolean canSafelySendC09() {
        return ticksSinceLastAttack >= 2
                && ticksSinceLastRightClick >= 2
                && ticksSinceLastRelease >= 2;
    }

    public void onTick(boolean usingItem, boolean moving, boolean sneaking, boolean inLiquid, int currentSlot, ItemStack[] hotbar) {
        onTick(usingItem, moving, sneaking, inLiquid, false, currentSlot, hotbar);
    }

    public void onTick(boolean usingItem, boolean moving, boolean sneaking, boolean inLiquid, boolean combatActive, int currentSlot, ItemStack[] hotbar) {
        this.combatActive = combatActive;
        ticksSinceLastAttack++;
        ticksSinceLastRightClick++;
        ticksSinceLastRelease++;

        long now = timeSupplier.getAsLong();

        if (pendingRestore) {
            if (canSafelySendC09()) {
                sender.accept(new C09PacketHeldItemChange(currentSlot));
                pendingRestore = false;
                reset();
                lastSwapTime = now;
                currentCooldown = DEFAULT_MIN_COOLDOWN_MS + (long) (random.nextDouble() * DEFAULT_MAX_COOLDOWN_RANDOM_MS);
            }
            return;
        }

        if (noSlowTick > 0 && lastSlot != -1 && currentSlot != lastSlot) {
            reset();
            pendingRestore = false;
            return;
        }

        boolean canSwap = usingItem && moving && !sneaking && !inLiquid && !combatActive;

        if (!canSwap) {
            if (noSlowTick > 0) {
                if (canSafelySendC09()) {
                    restoreSlot(currentSlot);
                } else {
                    pendingRestore = true;
                    noSlowTick = 0;
                }
            }
            if (!usingItem) {
                hasBypassed = false;
                lastSwapTime = 0L;
            }
            return;
        }

        if (noSlowTick == 0) {
            if (now - lastSwapTime >= currentCooldown && canSafelySendC09()) {
                int altSlot = getAlternateSlot(currentSlot, hotbar);
                if (altSlot != currentSlot) {
                    sender.accept(new C09PacketHeldItemChange(altSlot));
                    lastSlot = currentSlot;
                    swappedSlot = altSlot;
                    swapDelay = DEFAULT_MIN_SWAP_DELAY_TICKS + random.nextInt(DEFAULT_MAX_SWAP_DELAY_RANDOM_TICKS);
                    noSlowTick = 1;
                    hasBypassed = true;
                }
            }
        } else if (noSlowTick >= swapDelay) {
            if (canSafelySendC09()) {
                restoreSlot(currentSlot);
                lastSwapTime = now;
                currentCooldown = DEFAULT_MIN_COOLDOWN_MS + (long) (random.nextDouble() * DEFAULT_MAX_COOLDOWN_RANDOM_MS);
            } else {
                pendingRestore = true;
                noSlowTick = 0;
            }
        } else {
            noSlowTick++;
        }
    }

    public int getAlternateSlot(int currentSlot, ItemStack[] hotbar) {
        int slot = currentSlot;
        if (hotbar != null) {
            for (int attempts = 0; attempts < 32; attempts++) {
                int candidate = random.nextInt(9);
                if (candidate != currentSlot && candidate < hotbar.length && hotbar[candidate] != null) {
                    slot = candidate;
                    break;
                }
            }
        }
        if (slot == currentSlot) {
            slot = (currentSlot + 1 + random.nextInt(8)) % 9;
        }
        return slot;
    }

    public void restoreSlot(int currentSlot) {
        if (noSlowTick > 0 || swappedSlot != -1 || pendingRestore) {
            sender.accept(new C09PacketHeldItemChange(currentSlot));
        }
        reset();
        pendingRestore = false;
    }

    public void onDisable(int currentSlot) {
        hasBypassed = false;
        combatActive = false;
        restoreSlot(currentSlot);
    }

    public void onCorrection(int currentSlot) {
        hasBypassed = false;
        combatActive = false;
        restoreSlot(currentSlot);
        lastSwapTime = timeSupplier.getAsLong();
        currentCooldown = DEFAULT_MIN_COOLDOWN_MS + DEFAULT_MAX_COOLDOWN_RANDOM_MS;
    }

    public boolean canBypass() {
        return (hasBypassed || noSlowTick > 0)
                && ticksSinceLastRightClick > 0
                && !combatActive;
    }

    public void setHasBypassed(boolean hasBypassed) {
        this.hasBypassed = hasBypassed;
    }

    public boolean hasBypassed() {
        return hasBypassed;
    }

    public boolean isCombatActive() {
        return combatActive;
    }

    public void reset() {
        noSlowTick = 0;
        swappedSlot = -1;
        lastSlot = -1;
        pendingRestore = false;
    }

    public boolean isMidSwap() {
        return noSlowTick > 0 || pendingRestore;
    }

    public boolean isPendingRestore() {
        return pendingRestore;
    }

    public int getNoSlowTick() {
        return noSlowTick;
    }

    public int getSwapDelay() {
        return swapDelay;
    }

    public int getSwappedSlot() {
        return swappedSlot;
    }

    public long getLastSwapTime() {
        return lastSwapTime;
    }

    public long getCurrentCooldown() {
        return currentCooldown;
    }

    public int getTicksSinceLastAttack() {
        return ticksSinceLastAttack;
    }

    public int getTicksSinceLastRightClick() {
        return ticksSinceLastRightClick;
    }

    public int getTicksSinceLastRelease() {
        return ticksSinceLastRelease;
    }

    public void setTicksSinceLastAttack(int ticks) {
        this.ticksSinceLastAttack = ticks;
    }

    public void setTicksSinceLastRightClick(int ticks) {
        this.ticksSinceLastRightClick = ticks;
    }

    public void setTicksSinceLastRelease(int ticks) {
        this.ticksSinceLastRelease = ticks;
    }
}
