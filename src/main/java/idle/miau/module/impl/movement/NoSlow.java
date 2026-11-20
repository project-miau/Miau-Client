package idle.miau.module.impl.movement;

import idle.miau.event.PreMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.event.NoEventPacketEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.combat.KillAura;
import idle.miau.module.impl.movement.noslow.GrimTest;
import idle.miau.module.impl.movement.noslow.GrimBlink;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.BadPacketsComponent;
import idle.miau.utility.PacketUtils;
import idle.miau.utility.Utils;
import net.minecraft.item.Item;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

public class NoSlow extends Module {
    private static final int INTAVE_MODE = 1;
    private static final int GRIM_SEMI_MODE = 2;
    private static final int GRIM_TEST_MODE = 3;
    public static final int BLINK_MODE = 4;
    private static final String[] NOSLOW_MODES = {
            "Vanilla", "Intave", "GrimSemi", "GrimTest", "Blink"
    };

    public static SliderSetting mode;
    public static SliderSetting slowed;
    public static ButtonSetting sword;
    public static ButtonSetting consume;
    public static ButtonSetting bow;

    public boolean noSlowing;
    private static int grimTicks;
    private int intaveDisable;
    private boolean intaveConsumeBypassing;
    private int lastMode = -1;
    private final DescriptionSetting defaultDescription;
    private final GrimTest grimTest = new GrimTest(PacketUtils::sendPacketNoEvent);
    private final GrimBlink grimBlink = new GrimBlink(PacketUtils::sendPacketNoEvent);
    private NetHandlerPlayClient grimConnection;
    private WorldClient grimWorld;

    public NoSlow() {
        super("NoSlow", category.movement, 0);
        setDescription("Reduces movement slowdown while using items.");
        registerSetting(mode = new SliderSetting("Mode", 0, NOSLOW_MODES));
        registerSetting(sword = new ButtonSetting("Sword", false));
        registerSetting(consume = new ButtonSetting("Consume", false));
        registerSetting(bow = new ButtonSetting("Bow", false));
        registerSetting(defaultDescription = new DescriptionSetting(
                "Default is 80% motion reduction."));
        registerSetting(slowed = new SliderSetting(
                "Slow %", 80.0D, 0.0D, 80.0D, 1.0D));
        updateSettingVisibility();
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        flushGrimTest();
        if (mc.thePlayer != null) {
            grimBlink.onDisable(mc.thePlayer.inventory.currentItem);
        } else {
            grimBlink.reset();
        }
        noSlowing = false;
        resetState();
    }

    @Override
    public void guiUpdate() {
        updateSettingVisibility();
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            flushGrimTest();
            if (mc.thePlayer != null) {
                grimBlink.onDisable(mc.thePlayer.inventory.currentItem);
            } else {
                grimBlink.reset();
            }
            resetState();
            updateSettingVisibility();
        }
    }

    @Override
    public void onCommandSettingChanged(Setting setting) {
        if (setting == mode) {
            flushGrimTest();
            if (mc.thePlayer != null) {
                grimBlink.onDisable(mc.thePlayer.inventory.currentItem);
            } else {
                grimBlink.reset();
            }
            resetState();
        }
        updateSettingVisibility();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent event) {
        if (!Utils.nullCheck()) {
            resetState();
            return;
        }

        int currentMode = (int) mode.getInput();
        if (currentMode != lastMode) {
            flushGrimTest();
            if (mc.thePlayer != null) {
                grimBlink.onDisable(mc.thePlayer.inventory.currentItem);
            } else {
                grimBlink.reset();
            }
            resetModeState();
            lastMode = currentMode;
            updateSettingVisibility();
        }

        if (currentMode == BLINK_MODE) {
            boolean using = mc.thePlayer.isUsingItem() && isCurrentItemEnabled();
            boolean moving = (mc.thePlayer.movementInput != null
                    && (mc.thePlayer.movementInput.moveForward != 0.0F || mc.thePlayer.movementInput.moveStrafe != 0.0F))
                    || mc.gameSettings.keyBindForward.isKeyDown()
                    || mc.gameSettings.keyBindBack.isKeyDown()
                    || mc.gameSettings.keyBindLeft.isKeyDown()
                    || mc.gameSettings.keyBindRight.isKeyDown();
            boolean sneaking = mc.thePlayer.isSneaking() || mc.gameSettings.keyBindSneak.isKeyDown();
            boolean inLiquid = mc.thePlayer.isInWater() || mc.thePlayer.isInLava();
            boolean isBow = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemBow;

            if (isBow) {
                using = false;
            }

            boolean isSword = mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
            boolean autoblockActive = isSword && ModuleManager.autoblock != null
                    && ModuleManager.autoblock.isEnabled()
                    && (ModuleManager.autoblock.isBlocking()
                    || ModuleManager.autoblock.isActive());
            boolean combatActive = isSword && (autoblockActive
                    || KillAura.target != null
                    || KillAura.attackingEntity != null
                    || mc.thePlayer.isSwingInProgress
                    || mc.gameSettings.keyBindAttack.isKeyDown());

            int currentSlot = mc.thePlayer.inventory.currentItem;
            ItemStack[] hotbar = mc.thePlayer.inventory.mainInventory;

            grimBlink.onTick(using, moving, sneaking, inLiquid, combatActive, currentSlot, hotbar);
            return;
        }

        if (currentMode == GRIM_TEST_MODE && bindGrimSession()) {
            grimTest.releaseExpired(System.nanoTime());
        }

        if (currentMode == GRIM_SEMI_MODE) {
            if (!mc.thePlayer.isUsingItem() || !isCurrentItemEnabled()) {
                grimTicks = 0;
                return;
            }
            if (++grimTicks >= 2) {
                grimTicks = 0;
            }
            return;
        }

        grimTicks = 0;
        if (currentMode == INTAVE_MODE) {
            intaveDisable++;
            updateIntaveBypass();
        } else {
            intaveConsumeBypassing = false;
        }
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onSendPacket(SendPacketEvent event) {
        int currentMode = (int) mode.getInput();
        if (currentMode == BLINK_MODE) {
            grimBlink.onPacket(event.getPacket());
            return;
        }

        if (currentMode != INTAVE_MODE
                || !Utils.nullCheck()
                || !(event.getPacket() instanceof C08PacketPlayerBlockPlacement)
                || !isIntaveSwordActive()
                || BadPacketsComponent.bad(false, true, true, false, false)) {
            return;
        }
        sendIntaveSlotSwap();
    }

    @Subscribe(priority = Priority.LOW)
    public void onGrimTestPacket(SendPacketEvent event) {
        if ((int) mode.getInput() != GRIM_TEST_MODE || event.isCanceled() || !bindGrimSession()) {
            return;
        }
        boolean usingItem = mc.thePlayer.isUsingItem() && isCurrentItemEnabled()
                && !mc.thePlayer.isRiding() && mc.thePlayer.isEntityAlive();
        if (grimTest.handle(event.getPacket(), usingItem,
                mc.thePlayer.inventory.currentItem, System.nanoTime())) {
            event.setCanceled(true);
        }
    }

    @Subscribe
    public void onGrimTestNoEventPacket(NoEventPacketEvent event) {
        if ((int) mode.getInput() == BLINK_MODE) {
            grimBlink.onPacket(event.getPacket());
        }
        if ((int) mode.getInput() == GRIM_TEST_MODE && bindGrimSession()) {
            grimTest.observe(event.getPacket());
        }
    }

    @Subscribe(priority = Priority.HIGHEST, receiveCanceled = true)
    public void onGrimTestCorrection(ReceivePacketEvent event) {
        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            flushGrimTest();
            grimTest.reset();
            if (mc.thePlayer != null) {
                grimBlink.onCorrection(mc.thePlayer.inventory.currentItem);
            } else {
                grimBlink.reset();
            }
        }
    }

    private boolean bindGrimSession() {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            grimTest.reset();
            grimConnection = null;
            grimWorld = null;
            return false;
        }
        if (grimConnection != mc.getNetHandler() || grimWorld != mc.theWorld) {
            grimTest.reset();
            grimConnection = mc.getNetHandler();
            grimWorld = mc.theWorld;
        }
        return true;
    }

    private void flushGrimTest() {
        if (mc != null && grimConnection != null && mc.getNetHandler() == grimConnection
                && mc.theWorld == grimWorld && grimConnection.getNetworkManager().isChannelOpen()) {
            grimTest.flush();
        } else {
            grimTest.reset();
        }
    }

    public void clearGrimTest() {
        grimTest.reset();
        grimConnection = null;
        grimWorld = null;
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onPreMotion(PreMotionEvent event) {
        if ((int) mode.getInput() == INTAVE_MODE && isIntaveConsumableActive()) {
            PacketUtils.sendPacket(createIntaveConsumeRelease());
            intaveConsumeBypassing = true;
        }
    }

    public static float getSlowed() {
        if (mc.thePlayer == null
                || mc.thePlayer.getHeldItem() == null
                || ModuleManager.noSlow == null
                || !ModuleManager.noSlow.isEnabled()
                || !isCurrentItemEnabled()) {
            return 0.2F;
        }

        int currentMode = (int) mode.getInput();
        if (currentMode == BLINK_MODE) {
            Item item = mc.thePlayer.getHeldItem().getItem();
            if (item instanceof ItemBow) {
                return 0.2F;
            }
            return ModuleManager.noSlow.grimBlink.canBypass() ? 1.0F : 0.2F;
        }
        if (currentMode == GRIM_TEST_MODE) {
            return 1.0F;
        }
        if (currentMode == GRIM_SEMI_MODE) {
            return grimSemiMultiplier(grimTicks);
        }
        if (currentMode == INTAVE_MODE) {
            return intaveMultiplier(
                    mc.thePlayer.getHeldItem().getItem(),
                    ModuleManager.noSlow.intaveConsumeBypassing);
        }
        return (100.0F - (float) slowed.getInput()) / 100.0F;
    }

    public static boolean isNewGrimMode() {
        if (ModuleManager.noSlow == null || !ModuleManager.noSlow.isEnabled()
                || !isCurrentItemEnabled()) {
            return false;
        }
        int currentMode = (int) mode.getInput();
        if (currentMode == BLINK_MODE) {
            return mc.thePlayer != null
                    && mc.thePlayer.getHeldItem() != null
                    && !(mc.thePlayer.getHeldItem().getItem() instanceof ItemBow)
                    && ModuleManager.noSlow.grimBlink.canBypass();
        }
        return isGrimMode(currentMode);
    }

    static boolean isItemEnabled(
            Item item,
            int metadata,
            boolean swordEnabled,
            boolean consumeEnabled,
            boolean bowEnabled) {
        if (item instanceof ItemSword) {
            return swordEnabled;
        }
        if (item instanceof ItemFood) {
            return consumeEnabled;
        }
        if (item instanceof ItemPotion) {
            return consumeEnabled && !ItemPotion.isSplash(metadata);
        }
        return item instanceof ItemBow && bowEnabled;
    }

    static float intaveMultiplier(Item item, boolean consumeBypassing) {
        if (item instanceof ItemSword || item instanceof ItemBow) {
            return 1.0F;
        }
        if (item instanceof ItemFood || item instanceof ItemPotion) {
            return consumeBypassing ? 1.0F : 0.2F;
        }
        return 0.2F;
    }

    private static boolean isCurrentItemEnabled() {
        if (mc.thePlayer == null) {
            return false;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && isItemEnabled(
                stack.getItem(),
                stack.getMetadata(),
                sword.isToggled(),
                consume.isToggled(),
                bow.isToggled());
    }

    static boolean isGrimMode(int modeIndex) {
        return modeIndex == GRIM_SEMI_MODE || modeIndex == GRIM_TEST_MODE || modeIndex == BLINK_MODE;
    }

    static float grimMultiplier(boolean buffering, Item item) {
        return buffering && item instanceof ItemSword ? 1.0F : 0.2F;
    }

    static boolean grimShouldBufferPacket(Packet<?> packet) {
        return packet instanceof C03PacketPlayer
                || packet instanceof C02PacketUseEntity
                || packet instanceof C0APacketAnimation
                || packet instanceof net.minecraft.network.play.client.C0BPacketEntityAction
                || packet instanceof net.minecraft.network.play.client.C0FPacketConfirmTransaction;
    }

    static C08PacketPlayerBlockPlacement createGrimBlockPacket(ItemStack stack) {
        return new C08PacketPlayerBlockPlacement(
                new BlockPos(-1, -1, -1), 255, stack, 0.0F, 0.0F, 0.0F);
    }

    private boolean isIntaveSwordActive() {
        return sword.isToggled()
                && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private boolean isIntaveConsumableActive() {
        if (mc.thePlayer == null
                || !mc.thePlayer.isUsingItem()
                || mc.thePlayer.getHeldItem() == null
                || !consume.isToggled()
                || !isCurrentItemEnabled()) {
            return false;
        }
        Item item = mc.thePlayer.getHeldItem().getItem();
        return item instanceof ItemFood || item instanceof ItemPotion;
    }

    private void updateIntaveBypass() {
        intaveConsumeBypassing = isIntaveConsumableActive();
        if (!mc.thePlayer.isUsingItem()
                || !isCurrentItemEnabled()
                || intaveDisable <= 10
                || BadPacketsComponent.bad(false, true, true, false, false)
                || KillAura.target != null) {
            return;
        }
        if (isIntaveSwordActive()) {
            performIntaveSwordBypass();
        }
    }

    private void performIntaveSwordBypass() {
        sendIntaveSlotSwap();
        mc.thePlayer.sendQueue.addToSendQueue(
                new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
    }

    static C07PacketPlayerDigging createIntaveConsumeRelease() {
        return new C07PacketPlayerDigging(
                C07PacketPlayerDigging.Action.RELEASE_USE_ITEM,
                BlockPos.ORIGIN,
                EnumFacing.UP);
    }

    private void sendIntaveSlotSwap() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.sendQueue.addToSendQueue(
                new C09PacketHeldItemChange(currentSlot % 8 + 1));
        mc.thePlayer.sendQueue.addToSendQueue(
                new C09PacketHeldItemChange(currentSlot));
    }

    static float grimSemiMultiplier(int ticks) {
        return ticks == 0 ? 1.0F : 0.2F;
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        resetState();
    }

    private void resetModeState() {
        grimTest.reset();
        grimBlink.reset();
        grimTicks = 0;
        intaveConsumeBypassing = false;
    }

    private void resetState() {
        grimConnection = null;
        grimWorld = null;
        intaveDisable = 0;
        lastMode = -1;
        resetModeState();
    }

    private void updateSettingVisibility() {
        int currentMode = (int) mode.getInput();
        boolean vanilla = currentMode == 0;
        defaultDescription.setVisible(vanilla, this);
        slowed.setVisible(vanilla, this);
    }

    public GrimBlink getGrimBlink() {
        return grimBlink;
    }

    @Override
    public String getInfo() {
        int index = Math.max(0, Math.min(
                (int) mode.getInput(), NOSLOW_MODES.length - 1));
        return NOSLOW_MODES[index];
    }
}
