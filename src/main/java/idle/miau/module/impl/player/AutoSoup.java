package idle.miau.module.impl.player;

import idle.miau.event.PreAttackEvent;
import idle.miau.event.PreMouseSelectionEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ModuleActionService;
import idle.miau.utility.inventory.ConsumableAccess;
import idle.miau.utility.inventory.InventoryActions;
import idle.miau.utility.inventory.ItemActionTiming;
import idle.miau.utility.rotation.MiauRandom;

public final class AutoSoup extends Module {
    private final SliderSetting minHealth;
    private final SliderSetting maxHealth;
    private final SliderSetting delayRandomization;
    private final SliderSetting delayBefore;
    private final SliderSetting delayBetween;
    private final ButtonSetting discardBowls;
    private final State state = new State();
    private final ConsumableAccess.Live access = new ConsumableAccess.Live(this);
    private final ItemActionTiming delay = new ItemActionTiming();
    private final MiauRandom.Smooth skip = new MiauRandom.Smooth();

    public AutoSoup() {
        super("AutoSoup", category.player);
        setDescription("Uses soup at Min Health and heals toward Max Health.");
        registerSetting(minHealth = new SliderSetting("Min Health", 13.0, 0.0, 20.0, 0.1));
        registerSetting(maxHealth = new SliderSetting("Max Health", 20.0, 0.0, 20.0, 0.1));
        registerSetting(
                delayRandomization = new SliderSetting("Randomization", 0.5, 0.0, 1.0, 0.01));
        registerSetting(delayBefore = new SliderSetting("Switch Delay", 2.0, 0.0, 10.0, 0.1));
        registerSetting(delayBetween = new SliderSetting("Use Delay", 0.5, 0.0, 2.0, 0.01));
        registerSetting(discardBowls = new ButtonSetting("Drop bowls", true));
    }

    @Override
    public void onKeyBind() {
        if (!isHoldBind()) {
            super.onKeyBind();
        } else if (InventoryActions.bindingPressed(getKeycode())) {
            enable();
        } else if (isEnabled()) {
            disable();
        }
    }

    @Override
    public void onEnable() {
        state.enable();
    }

    @Override
    public void disable() {
        if (!isEnabled() || !state.cancelDisable()) {
            super.disable();
        }
    }

    @Subscribe
    public void onTick(PreMouseSelectionEvent event) {
        if (!InventoryActions.inputReady() || mc.currentScreen != null) {
            access.cleanup();
            state.abort(access);
            return;
        }
        state.tick(
                access,
                new Policy() {
                    public boolean matches(int type) {
                        return type == 282 || type == 413;
                    }

                    public boolean skipCandidate() {
                        return skip.gate(
                                0.01F, 0.08F, 30.0, MiauRandom.now(), MiauRandom.SHARED);
                    }

                    public float delay(float base) {
                        return delay.soup(
                                base,
                                (float) delayRandomization.getInput(),
                                MiauRandom.now(),
                                MiauRandom.SHARED);
                    }
                },
                (float) minHealth.getInput(),
                (float) maxHealth.getInput(),
                (float) delayBefore.getInput(),
                (float) delayBetween.getInput(),
                discardBowls.isToggled());
    }

    @Subscribe
    public void onPreAttack(PreAttackEvent event) {
        if (ModuleActionService.owner() == this) {
            event.setCanceled(true);
        }
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == minHealth && minHealth.getInput() > maxHealth.getInput()) {
            maxHealth.setValue(minHealth.getInput());
        } else if (setting == maxHealth && maxHealth.getInput() < minHealth.getInput()) {
            minHealth.setValue(maxHealth.getInput());
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting instanceof SliderSetting) {
            onSlide((SliderSetting) setting);
        }
    }

    @Override
    public void onDisable() {
        access.cleanup();
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        access.cleanup();
        state.abort(access);
    }

    public interface Policy {
        boolean matches(int type);

        boolean skipCandidate();

        float delay(float base);
    }

    public static final class State {
        private static final float SOUP_HEAL = 7.0F;
        private static final int RESPONSE_TICKS = 10;
        private Phase phase = Phase.IDLE;
        private int selected = -1;
        private int previous = -1;
        private int selectedType;
        private int usedSlots;
        private int uses;
        private int responseTicks;
        private float healthBeforeUse;
        private float expectedHealth;
        private float timer;
        private boolean cancel;
        private boolean ownsSlot;

        public void enable() {
            phase = Phase.IDLE;
            selected = previous = -1;
            usedSlots = uses = responseTicks = 0;
            healthBeforeUse = expectedHealth = timer = 0.0F;
            cancel = ownsSlot = false;
        }

        private void abort(ConsumableAccess access) {
            boolean disabling = cancel;
            enable();
            if (disabling) {
                access.disable();
            }
        }

        public boolean cancelDisable() {
            if (phase == Phase.IDLE) {
                return false;
            }
            cancel = true;
            return true;
        }

        private float health(ConsumableAccess access) {
            float current = access.health();
            return uses > 0 && current == healthBeforeUse
                    ? Math.max(current, expectedHealth)
                    : current;
        }

        private boolean shouldUse(
                ConsumableAccess access, float minHealth, float maxHealth, boolean additional) {
            float lower = Math.min(minHealth, maxHealth);
            float upper = Math.min(access.maxHealth(), Math.max(minHealth, maxHealth));
            float trigger = lower > 0.0F ? Math.min(lower, access.maxHealth()) : upper;
            float goal = upper > trigger ? upper : access.maxHealth();
            float health = additional ? health(access) : access.health();
            return health > 0.0F
                    && health < access.maxHealth()
                    && (additional
                            ? health < goal && (health <= trigger || goal - health >= SOUP_HEAL)
                            : health <= trigger);
        }

        private int findSoup(ConsumableAccess access, Policy policy) {
            int start = access.current();
            int found = -1;
            for (int offset = 0; offset < 9; offset++) {
                int slot = (start + offset) % 9;
                if ((usedSlots & 1 << slot) == 0 && policy.matches(access.type(slot))) {
                    found = slot;
                    if (!policy.skipCandidate()) {
                        break;
                    }
                }
            }
            return found;
        }

        private void finish(ConsumableAccess access, Policy policy, float useDelay) {
            if (ownsSlot) {
                if (access.current() == selected) {
                    access.select(previous);
                }
                access.release();
            }
            ownsSlot = false;
            selected = previous = -1;
            uses = usedSlots = responseTicks = 0;
            healthBeforeUse = expectedHealth = 0.0F;
            phase = Phase.IDLE;
            timer = policy.delay(useDelay);
            if (cancel) {
                access.disable();
            }
        }

        public void tick(
                ConsumableAccess access,
                Policy policy,
                float minHealth,
                float maxHealth,
                float switchDelay,
                float useDelay,
                boolean drop) {
            if (!access.ready()) {
                finish(access, policy, useDelay);
                enable();
                return;
            }
            if (ownsSlot && access.current() != selected) {
                finish(access, policy, useDelay);
                return;
            }
            if (timer > 0.0F && --timer > 0.0F) {
                return;
            }
            for (int pass = 0; pass < 4; pass++) {
                switch (phase) {
                    case IDLE:
                        if (cancel) {
                            access.disable();
                            return;
                        }
                        if (!shouldUse(access, minHealth, maxHealth, false)) {
                            return;
                        }
                        uses = usedSlots = 0;
                        phase = Phase.SELECTING;
                        timer = policy.delay(switchDelay);
                        break;
                    case SELECTING:
                        if (cancel || !shouldUse(access, minHealth, maxHealth, uses > 0)) {
                            finish(access, policy, useDelay);
                            return;
                        }
                        int slot = findSoup(access, policy);
                        if (slot < 0) {
                            finish(access, policy, useDelay);
                            return;
                        }
                        if (!access.claim()) {
                            phase = Phase.IDLE;
                            return;
                        }
                        if (!ownsSlot) {
                            previous = access.current();
                            access.remember();
                            ownsSlot = true;
                        }
                        selected = slot;
                        selectedType = access.type(slot);
                        if (slot != access.current()) {
                            access.select(slot);
                        }
                        phase = Phase.USING;
                        timer = policy.delay(useDelay);
                        if (access.usingItem()) {
                            timer = Math.max(1.0F, timer);
                        }
                        break;
                    case USING:
                        if (cancel
                                || access.current() != selected
                                || !policy.matches(access.type(selected))
                                || !shouldUse(access, minHealth, maxHealth, uses > 0)) {
                            finish(access, policy, useDelay);
                            return;
                        }
                        float health = health(access);
                        healthBeforeUse = access.health();
                        expectedHealth = Math.min(access.maxHealth(), health + SOUP_HEAL);
                        access.use();
                        uses++;
                        usedSlots |= 1 << selected;
                        responseTicks = 0;
                        phase = Phase.CONFIRMING;
                        timer = Math.max(1.0F, policy.delay(useDelay));
                        return;
                    case CONFIRMING:
                        if (access.type(selected) == selectedType) {
                            if (++responseTicks >= RESPONSE_TICKS || cancel) {
                                expectedHealth = access.health();
                                finish(access, policy, useDelay);
                            }
                            return;
                        }
                        phase =
                                drop && access.type(selected) == 281
                                        ? Phase.DROPPING
                                        : Phase.CONTINUING;
                        break;
                    case DROPPING:
                        if (access.type(selected) == 281) {
                            access.drop();
                        }
                        phase = Phase.CONTINUING;
                        timer = Math.max(1.0F, policy.delay(useDelay));
                        return;
                    case CONTINUING:
                        if (cancel || uses >= 2 || !shouldUse(access, minHealth, maxHealth, true)) {
                            finish(access, policy, useDelay);
                            return;
                        }
                        phase = Phase.SELECTING;
                        break;
                    default:
                        throw new IllegalStateException("Invalid soup phase");
                }
                if (timer > 0.0F) {
                    return;
                }
            }
        }

        private enum Phase {
            IDLE,
            SELECTING,
            USING,
            CONFIRMING,
            DROPPING,
            CONTINUING
        }
    }
}
