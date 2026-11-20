package idle.miau.module.impl.player;

import idle.miau.event.ControllerUpdateEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.KeySetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ModuleActionService;
import idle.miau.utility.MiauMath;
import idle.miau.utility.inventory.InventoryActions;
import idle.miau.utility.inventory.RefillSelection;
import idle.miau.utility.rotation.MiauRandom;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemStack;

import java.util.Arrays;

public final class Refill extends Module {
    private final KeySetting activeKeybind;
    private final ButtonSetting potions;
    private final ButtonSetting soups;
    private final SliderSetting openDelay;
    private final SliderSetting closeDelay;
    private final SliderSetting minSpeed;
    private final SliderSetting maxSpeed;
    private final ButtonSetting dynamic;
    private final State state = new State();
    private final MiauRandom.Smooth openRandom = new MiauRandom.Smooth();
    private final MiauRandom.Smooth closeRandom = new MiauRandom.Smooth();
    private final MiauRandom.Smooth speedRandom = new MiauRandom.Smooth();
    private final MiauRandom.Smooth speedGate = new MiauRandom.Smooth();
    private final MiauRandom.Smooth emptyGate = new MiauRandom.Smooth();
    private final MiauRandom.Smooth secondGate = new MiauRandom.Smooth();
    private final MiauRandom.Gaussian wave = new MiauRandom.Gaussian();
    private boolean active;
    private boolean keyDown;
    private boolean openedInventory;
    private boolean automaticCycle;
    private boolean autoTriggered;

    public Refill() {
        super("Refill", category.player);
        setDescription("Automatically refill items in your inventory.");
        registerSetting(activeKeybind = new KeySetting("Active Keybind", 0));
        registerSetting(potions = new ButtonSetting("Potions", true));
        registerSetting(soups = new ButtonSetting("Soups", true));
        registerSetting(openDelay = new SliderSetting("Open Delay", 2.0, 0.0, 10.0, 0.1));
        registerSetting(closeDelay = new SliderSetting("Close Delay", 1.0, 0.0, 5.0, 0.1));
        registerSetting(minSpeed = new SliderSetting("Min Speed", 1.5, 1.0, 10.0, 0.1));
        registerSetting(maxSpeed = new SliderSetting("Max Speed", 2.5, 1.0, 10.0, 0.1));
        registerSetting(dynamic = new ButtonSetting("Dynamic", true));
        guiUpdate();
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        guiUpdate();
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting instanceof SliderSetting) {
            onSlide((SliderSetting) setting);
        }
        guiUpdate();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == minSpeed && minSpeed.getInput() > maxSpeed.getInput()) {
            maxSpeed.setValue(minSpeed.getInput());
        } else if (setting == maxSpeed && maxSpeed.getInput() < minSpeed.getInput()) {
            minSpeed.setValue(maxSpeed.getInput());
        }
    }

    @Override
    public void onEnable() {
        state.clear();
        active = false;
        keyDown = false;
        openedInventory = false;
        automaticCycle = false;
        autoTriggered = false;
    }

    @Override
    public void onDisable() {
        finish();
        state.clear();
        keyDown = false;
        autoTriggered = false;
    }

    private void finish() {
        if (automaticCycle && mc.thePlayer != null && hasSoup(0, 9)) {
            autoTriggered = false;
        }
        if (openedInventory) {
            InventoryActions.pressCount(mc.gameSettings.keyBindInventory, 0);
            if (mc.currentScreen instanceof GuiInventory) {
                InventoryActions.escape(mc.currentScreen);
            }
        }
        active = false;
        openedInventory = false;
        automaticCycle = false;
        ModuleActionService.release(this);
    }

    @Override
    public void guiUpdate() {
        minSpeed.setVisible(!dynamic.isToggled(), this);
        maxSpeed.setVisible(!dynamic.isToggled(), this);
    }

    private boolean needsSoup() {
        if (!soups.isToggled() || hasSoup(0, 9) || !hasSoup(9, 36)) {
            return false;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (InventoryActions.type(mc.thePlayer.inventory.getStackInSlot(slot)) == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSoup(int start, int end) {
        for (int slot = start; slot < end; slot++) {
            if (RefillSelection.isSoup(
                    InventoryActions.type(mc.thePlayer.inventory.getStackInSlot(slot)))) {
                return true;
            }
        }
        return false;
    }

    private static double subtractDelay(float setting, float sample) {
        double extra = (double) setting - (double) sample;
        return extra > 0.0 ? extra : 0.0;
    }

    @Subscribe
    public void onControllerUpdate(ControllerUpdateEvent event) {
        boolean pressed = activeKeybind.isPressed();
        boolean start = pressed && !keyDown;
        keyDown = pressed;
        if (!active) {
            if (!InventoryActions.inputReady() || mc.currentScreen != null) {
                return;
            }
            boolean automatic = activeKeybind.getKey() == 0 && needsSoup();
            if (!automatic) {
                autoTriggered = false;
            }
            if (!start && (!automatic || autoTriggered) || !ModuleActionService.claim(this)) {
                return;
            }
            state.clear();
            active = true;
            automaticCycle = automatic;
            autoTriggered = automatic;
        }
        Refill.Access access =
                new Refill.Access() {
                    @Override
                    public boolean screen() {
                        return mc.currentScreen != null;
                    }

                    @Override
                    public boolean inventoryScreen() {
                        return mc.currentScreen instanceof GuiInventory;
                    }

                    @Override
                    public boolean player() {
                        return mc.thePlayer != null;
                    }

                    @Override
                    public int type(int slot) {
                        return InventoryActions.type(mc.thePlayer.inventory.getStackInSlot(slot));
                    }

                    @Override
                    public int damage(int slot) {
                        ItemStack s = mc.thePlayer.inventory.getStackInSlot(slot);
                        return s == null ? 0 : s.getItemDamage();
                    }

                    @Override
                    public void open() {
                        openedInventory = true;
                        InventoryActions.press(mc.gameSettings.keyBindInventory);
                    }

                    @Override
                    public void click(int slot, int button) {
                        InventoryActions.click((GuiInventory) mc.currentScreen, slot, button, 1);
                    }

                    @Override
                    public void finish() {
                        Refill.this.finish();
                    }
                };
        if (this.state.tick(
                access,
                new Refill.Policy(),
                soups.isToggled(),
                potions.isToggled() && !automaticCycle,
                (float) openDelay.getInput(),
                (float) closeDelay.getInput(),
                (float) minSpeed.getInput(),
                (float) maxSpeed.getInput(),
                dynamic.isToggled())) {
            InventoryActions.drainInput();
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        finish();
        this.state.clear();
        keyDown = false;
        autoTriggered = false;
    }

    public interface Access {
        boolean screen();

        boolean inventoryScreen();

        boolean player();

        int type(int slot);

        int damage(int slot);

        void open();

        void click(int slot, int button);

        void finish();
    }

    private final class Policy implements Refill.RandomPolicy {
        private final double now = MiauRandom.now();
        private final MiauRandom r = MiauRandom.SHARED;

        private Policy() {}

        @Override
        public boolean skipEmpty() {
            return this.r.chance(0.5F);
        }

        @Override
        public boolean chooseEmpty() {
            return Refill.this.emptyGate.gate(0.05F, 0.1F, 20.0, this.now, this.r);
        }

        @Override
        public boolean chooseSecond() {
            return Refill.this.secondGate.gate(0.05F, 0.1F, 10.0, this.now, this.r);
        }

        @Override
        public float index() {
            return this.r.interval(0.0F, 27.0F);
        }

        @Override
        public float angle() {
            return this.r.interval(0.0F, (float) Math.PI);
        }

        @Override
        public double openDelay(float setting) {
            return Refill.subtractDelay(
                    setting, Refill.this.openRandom.sample(0.0F, 2.0F, 30.0, this.now, this.r));
        }

        @Override
        public double closeDelay(float setting) {
            return Refill.subtractDelay(
                    setting, Refill.this.closeRandom.sample(0.0F, 2.0F, 30.0, this.now, this.r));
        }

        @Override
        public float speed(float minSpeed, float maxSpeed, boolean dynamic) {
            if (!dynamic) {
                return r.interval(Math.min(minSpeed, maxSpeed), Math.max(minSpeed, maxSpeed));
            }
            float result =
                    Refill.this.speedRandom.sample(0.8F, 1.2F, 30.0, this.now, this.r) * 2.0F;
            result *= 1.0F + Refill.this.wave.sample(this.r) * 0.3F;
            if (Refill.this.speedGate.gate(0.04F, 0.08F, 30.0, this.now, this.r)) {
                result /= this.r.interval(1.4F, 1.8F);
            }

            return Math.max(0.1F, Math.min(10.0F, result));
        }
    }

    public interface RandomPolicy extends RefillSelection.Gates {
        float index();

        float angle();

        double openDelay(float setting);

        double closeDelay(float setting);

        float speed(float minSpeed, float maxSpeed, boolean dynamic);
    }

    public static final class State {
        public final byte[] mask = new byte[27];
        public int phase;
        public int previous;
        public int selected;
        public float timer;
        public float angle;

        public boolean tick(
                Refill.Access a,
                Refill.RandomPolicy random,
                boolean soups,
                boolean pots,
                float openDelay,
                float closeDelay,
                float minSpeed,
                float maxSpeed,
                boolean dynamic) {
            boolean cancel = this.phase != 0;
            if (!cancel || a.screen() && a.inventoryScreen()) {
                if (this.timer > 0.0F) {
                    this.timer += -1.0F;
                    return cancel;
                } else if (!(this.timer <= 0.0F)) {
                    return cancel;
                } else {
                    while (this.phase != 4) {
                        int empty = 0;
                        if (a.player()) {
                            while (empty < 9 && a.type(empty) != 0) {
                                empty++;
                            }
                        }

                        if (!a.player() || empty == 9) {
                            this.timer =
                                    (float) ((double) this.timer + random.closeDelay(closeDelay));
                            this.phase = 4;
                            if (!(this.timer <= 0.0F)) {
                                return cancel;
                            } else {
                                a.finish();
                                return cancel;
                            }
                        }

                        switch (this.phase) {
                            case 0:
                                this.phase = 1;
                                if (a.screen()) {
                                    return cancel;
                                }

                                for (int slot = 0; slot < 27; slot++) {
                                    this.mask[slot] =
                                            (byte)
                                                    RefillSelection.classify(
                                                            a.type(slot + 9),
                                                            a.damage(slot + 9),
                                                            soups,
                                                            pots);
                                }

                                RefillSelection.Result candidate =
                                        RefillSelection.choose(
                                                this.mask, this.previous, this.angle, random);
                                this.angle = candidate.bearing;
                                if (candidate.slot == -1) {
                                    a.finish();
                                    return cancel;
                                }

                                a.open();
                                return cancel;
                            case 1:
                                this.previous = MiauMath.truncateInt(random.index());
                                this.angle = random.angle();
                                this.phase = 2;
                                this.timer =
                                        (float) ((double) this.timer + random.openDelay(openDelay));
                                break;
                            case 2:
                                RefillSelection.Result result =
                                        RefillSelection.choose(
                                                this.mask, this.previous, this.angle, random);
                                this.selected = result.slot;
                                this.angle = result.bearing;
                                if (this.selected == -1) {
                                    this.timer =
                                            (float)
                                                    ((double) this.timer
                                                            + random.closeDelay(closeDelay));
                                    this.phase = 4;
                                    return cancel;
                                }

                                float speed = random.speed(minSpeed, maxSpeed, dynamic);
                                float distance = dynamic ? result.score : 1.0F;
                                this.timer += distance / speed;
                                this.previous = this.selected;
                                this.phase = 3;
                                break;
                            case 3:
                                a.click(this.selected + 9, 1);
                                this.mask[this.selected] = 0;
                                this.phase = 2;
                                break;
                            default:
                                throw new IllegalStateException("Invalid refill phase");
                        }

                        if (!(this.timer <= 0.0F)) {
                            return cancel;
                        }
                    }

                    a.finish();
                    return cancel;
                }
            } else {
                a.finish();
                return false;
            }
        }

        public void clear() {
            this.phase = 0;
            this.previous = 0;
            this.selected = 0;
            this.timer = 0.0F;
            this.angle = 0.0F;
            Arrays.fill(this.mask, (byte) 0);
        }
    }
}
