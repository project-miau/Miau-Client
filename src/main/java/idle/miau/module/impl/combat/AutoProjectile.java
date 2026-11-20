package idle.miau.module.impl.combat;

import idle.miau.event.PreAttackEvent;
import idle.miau.event.PreMouseSelectionEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ModuleActionService;
import idle.miau.utility.WeaponPolicy;
import idle.miau.utility.inventory.InventoryActions;
import idle.miau.utility.inventory.ItemActionTiming;
import idle.miau.utility.rotation.MiauRandom;

import net.minecraft.entity.player.InventoryPlayer;

public final class AutoProjectile extends Module {
    private final SliderSetting throwSpeed;
    private final SliderSetting actionDelay;
    private final ButtonSetting randomizeDelays;
    private final ButtonSetting alwaysSwitchToWeapon;
    private final ItemActionTiming switchDelay = new ItemActionTiming();
    private final MiauRandom.Smooth throwRandom = new MiauRandom.Smooth();
    private final MiauRandom.Smooth throwGate = new MiauRandom.Smooth();
    private InventoryPlayer owner;
    private int previous = -1;
    private int selected;
    private int phase;
    private float timer;

    public AutoProjectile() {
        super("AutoProjectile", category.combat);
        setDescription(
                "Automatically throws snowballs or eggs.");
        registerSetting(throwSpeed = new SliderSetting("Throw speed", 5.0, 4.0, 20.0, 0.1));
        registerSetting(
                actionDelay = new SliderSetting("Delay between actions", 0.5, 0.0, 2.0, 0.01));
        registerSetting(randomizeDelays = new ButtonSetting("Randomize delays", true));
        registerSetting(
                alwaysSwitchToWeapon = new ButtonSetting("Always switch back to weapon", false));
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
        reset(true);
    }

    @Override
    public void onDisable() {
        reset(true);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        reset(true);
    }

    @Subscribe
    public void onPreAttack(PreAttackEvent event) {
        if (ModuleActionService.owner() == this) {
            event.setCanceled(true);
        }
    }

    private int scan(InventoryPlayer inventory) {
        for (int slot = 0; slot < 9; slot++) {
            int type = InventoryActions.type(inventory.getStackInSlot(slot));
            if (type == 332 || type == 344) {
                return slot;
            }
        }
        return -1;
    }

    private float delay() {
        return randomizeDelays.isToggled()
                ? switchDelay.switching(
                        (float) actionDelay.getInput(),
                        MiauRandom.now(),
                        MiauRandom.SHARED)
                : (float) actionDelay.getInput();
    }

    private float throwingDelay() {
        float speed = (float) throwSpeed.getInput();
        if (!randomizeDelays.isToggled()) {
            return 20.0F / speed;
        }
        double now = MiauRandom.now();
        MiauRandom random = MiauRandom.SHARED;
        float result = 20.0F / (speed * throwRandom.sample(0.5F, 1.0F, 1.0, now, random));
        if (throwGate.gate(0.01F, 0.03F, 30.0, now, random)) {
            result += random.interval(1.0F, 2.0F);
        }
        return result;
    }

    @Subscribe
    public void onTick(PreMouseSelectionEvent event) {
        if (!InventoryActions.inputReady() || mc.currentScreen != null) {
            reset(true);
            return;
        }
        for (int pass = 0; pass < 3; pass++) {
            if (timer > 0.0F && --timer > 0.0F) {
                return;
            }
            InventoryPlayer inventory = mc.thePlayer.inventory;
            if (phase >= 2 && inventory.currentItem != selected) {
                reset(false);
                if (!isHoldBind()) {
                    disable();
                }
                return;
            }
            switch (phase) {
                case 0:
                    int slot = scan(inventory);
                    if (slot < 0 || !ModuleActionService.claim(this)) {
                        if (!isHoldBind()) {
                            disable();
                        }
                        return;
                    }
                    owner = inventory;
                    previous = inventory.currentItem;
                    selected = slot;
                    if (alwaysSwitchToWeapon.isToggled()) {
                        int weapon = WeaponPolicy.SHARED.firstAllowedSlot(inventory);
                        if (weapon != -1) {
                            previous = weapon;
                        }
                    }
                    phase = selected == inventory.currentItem ? 2 : 1;
                    break;
                case 1:
                    inventory.currentItem = selected;
                    phase = 2;
                    timer += delay();
                    break;
                case 2:
                    int next = scan(inventory);
                    if (next < 0) {
                        phase = 3;
                        return;
                    }
                    if (next != inventory.currentItem) {
                        selected = next;
                        phase = 1;
                        timer += delay();
                        break;
                    }
                    InventoryActions.pressCount(mc.gameSettings.keyBindUseItem, 1);
                    if (isHoldBind()) {
                        timer += throwingDelay();
                    } else {
                        phase = 3;
                        timer += delay();
                    }
                    return;
                case 3:
                    restore();
                    ModuleActionService.release(this);
                    phase = 0;
                    timer += delay();
                    if (!isHoldBind()) {
                        disable();
                    }
                    return;
                default:
                    throw new IllegalStateException("Invalid projectile phase");
            }
        }
    }

    private void restore() {
        if (owner != null && previous >= 0) {
            owner.currentItem = previous;
        }
        owner = null;
        previous = -1;
    }

    private void reset(boolean restore) {
        if (restore) {
            restore();
        } else {
            owner = null;
            previous = -1;
        }
        ModuleActionService.release(this);
        phase = 0;
        timer = 0.0F;
    }
}
