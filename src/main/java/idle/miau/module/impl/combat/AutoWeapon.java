package idle.miau.module.impl.combat;

import idle.miau.event.AttackEvent;
import idle.miau.event.PrePlayerInteractEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.ReceivePacketEvent;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatTargeting;
import idle.miau.utility.ItemSortScoring;
import idle.miau.utility.Utils;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

public class AutoWeapon extends Module {
    private static final long MIN_DAMAGE_CONFIRMATION_TIMEOUT_MS = 1000L;
    private static final long MAX_DAMAGE_CONFIRMATION_TIMEOUT_MS = 5000L;

    private final SliderSetting activationTime;
    private final GroupSetting attributeSwap;
    private final ButtonSetting swapStick;
    private final ButtonSetting swapSword;
    private final ButtonSetting swapAxe;
    private final ButtonSetting swapPickaxe;
    private final ButtonSetting swapShovel;
    private final ButtonSetting swapHoe;
    private final ButtonSetting swapFishingRod;
    private final ButtonSetting swapBow;
    private final ButtonSetting[] attributeWeapons;
    private final ButtonSetting requireLeftMouse;

    private EntityPlayer target;
    private long targetStartTime;
    private int cycleTargetId = -1;
    private boolean usePrimaryWeapon;
    private EntityPlayer pendingDamageTarget;
    private int pendingTargetHurtTime;
    private long pendingAttackAt = -1L;
    private boolean killAuraCycle;

    public AutoWeapon() {
        super("Auto Weapon", category.combat);
        setDescription("Selects a priority weapon when attacking.");

        this.registerSetting(activationTime = new SliderSetting("Activation time", "ms", 0.0D, 0.0D, 1000.0D, 25.0D));
        this.registerSetting(new DescriptionSetting("Weapon priority"));
        this.registerSetting(attributeSwap = new GroupSetting("Attribute Swap"));
        this.registerSetting(swapStick = new ButtonSetting(attributeSwap, "Stick", false, "Attribute Swap"));
        this.registerSetting(swapSword = new ButtonSetting(attributeSwap, "Sword", false));
        this.registerSetting(swapAxe = new ButtonSetting(attributeSwap, "Axe", false));
        this.registerSetting(swapPickaxe = new ButtonSetting(attributeSwap, "Pickaxe", false));
        this.registerSetting(swapShovel = new ButtonSetting(attributeSwap, "Shovel", false));
        this.registerSetting(swapHoe = new ButtonSetting(attributeSwap, "Hoe", false));
        this.registerSetting(swapFishingRod = new ButtonSetting(attributeSwap, "Fishing Rod", false, "Rod"));
        this.registerSetting(swapBow = new ButtonSetting(attributeSwap, "Bow", false));
        attributeWeapons = new ButtonSetting[]{
                swapStick,
                swapSword,
                swapAxe,
                swapPickaxe,
                swapShovel,
                swapHoe,
                swapFishingRod,
                swapBow
        };
        this.registerSetting(new DescriptionSetting("Conditions"));
        this.registerSetting(requireLeftMouse = new ButtonSetting("Require Left mouse", true, "Require mouse down"));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        resetTarget();
        resetSwapCycle();
    }

    @Override
    public void onDisable() {
        resetTarget();
        resetSwapCycle();
    }

    @Override
    public void guiButtonToggled(ButtonSetting button) {
        if (!isAttributeWeaponButton(button)) {
            return;
        }

        if (button.isToggled()) {
            for (ButtonSetting weapon : attributeWeapons) {
                if (weapon != button) {
                    weapon.disable();
                }
            }
        }
        resetSwapCycle();
    }

    @Subscribe
    public void onPrePlayerInteract(PrePlayerInteractEvent event) {
        if (!Utils.nullCheck() || mc.currentScreen != null || !mc.inGameHasFocus || mc.thePlayer.isDead) {
            resetTarget();
            if (!Utils.nullCheck()) {
                resetSwapCycle();
            }
            return;
        }

        EntityPlayer nextTarget = getTarget();
        if (nextTarget != target) {
            target = nextTarget;
            targetStartTime = target == null ? 0L : System.currentTimeMillis();
        }

        if (target == null) {
            if (killAuraCycle && getKillAuraTarget() == null) {
                resetSwapCycle();
            }
            return;
        }

        if (target.getEntityId() != cycleTargetId) {
            beginSwapCycle(target);
        }

        confirmDamageFromHurtTime();

        if (requireLeftMouse.isToggled() && !Mouse.isButtonDown(0)) {
            return;
        }

        if (System.currentTimeMillis() - targetStartTime < activationTime.getInput()) {
            return;
        }

        int weaponSlot = !usePrimaryWeapon && hasSelectedAttributeWeapon()
                ? findBestAttributeWeapon()
                : findBestFallbackWeapon();
        if (weaponSlot == -1) {
            weaponSlot = findBestFallbackWeapon();
        }
        if (weaponSlot != -1) {
            selectSlot(weaponSlot);
        }
    }

    @Subscribe(priority = Priority.LOWEST, receiveCanceled = true)
    public void onAttack(AttackEvent event) {
        if (event.isCanceled()
                || !hasSelectedAttributeWeapon()
                || !Utils.nullCheck()
                || event.attacker != mc.thePlayer
                || !(event.target instanceof EntityPlayer)
                || mc.currentScreen != null
                || !mc.inGameHasFocus
                || mc.thePlayer.isDead
                || requireLeftMouse.isToggled() && !Mouse.isButtonDown(0)) {
            return;
        }

        EntityPlayer attackedPlayer = CombatTargeting.asValidPlayer(event.target, Double.MAX_VALUE);
        if (attackedPlayer == null) {
            return;
        }

        if (attackedPlayer != target) {
            target = attackedPlayer;
            targetStartTime = System.currentTimeMillis();
        }
        if (System.currentTimeMillis() - targetStartTime < activationTime.getInput()) {
            return;
        }

        if (attackedPlayer.getEntityId() != cycleTargetId) {
            beginSwapCycle(attackedPlayer);
        }

        if (usePrimaryWeapon) {
            return;
        }

        ItemStack heldStack = mc.thePlayer.getHeldItem();
        if (!isSelectedAttributeWeapon(heldStack)) {
            int attributeSlot = findBestAttributeWeapon();
            if (attributeSlot == -1) {
                return;
            }
            selectSlot(attributeSlot);
        }

        pendingDamageTarget = attackedPlayer;
        pendingTargetHurtTime = attackedPlayer.hurtTime;
        pendingAttackAt = System.currentTimeMillis();
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onPreUpdate(PreUpdateEvent event) {
        if (!killAuraCycle) {
            return;
        }

        EntityPlayer auraTarget = getKillAuraTarget();
        if (auraTarget == null || auraTarget.getEntityId() != cycleTargetId) {
            resetTarget();
            resetSwapCycle();
        }
    }

    @Subscribe(priority = Priority.LOWEST, receiveCanceled = true)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (usePrimaryWeapon
                || pendingDamageTarget == null
                || !Utils.nullCheck()
                || !(event.getPacket() instanceof S19PacketEntityStatus)) {
            return;
        }

        if (System.currentTimeMillis() - pendingAttackAt > getDamageConfirmationTimeoutMs()) {
            clearPendingDamage();
            return;
        }

        S19PacketEntityStatus status = (S19PacketEntityStatus) event.getPacket();
        if (status.getOpCode() != 2) {
            return;
        }

        Entity damagedEntity = status.getEntity(mc.theWorld);
        if (damagedEntity == pendingDamageTarget) {
            completeAttributeHit();
        }
    }

    private void confirmDamageFromHurtTime() {
        if (usePrimaryWeapon || pendingDamageTarget == null) {
            return;
        }

        if (System.currentTimeMillis() - pendingAttackAt > getDamageConfirmationTimeoutMs()) {
            clearPendingDamage();
            return;
        }

        if (pendingDamageTarget.hurtTime > pendingTargetHurtTime) {
            completeAttributeHit();
        }
    }

    private void completeAttributeHit() {
        usePrimaryWeapon = true;
        clearPendingDamage();

        int primarySlot = findBestFallbackWeapon();
        if (primarySlot != -1) {
            mc.thePlayer.inventory.currentItem = primarySlot;
        }
    }

    private EntityPlayer getTarget() {
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled()) {
            return getKillAuraTarget();
        }

        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || mc.objectMouseOver.entityHit == null) {
            return null;
        }

        return CombatTargeting.asValidPlayer(mc.objectMouseOver.entityHit, Double.MAX_VALUE);
    }

    private EntityPlayer getKillAuraTarget() {
        if (ModuleManager.killAura == null
                || !ModuleManager.killAura.isEnabled()
                || !(KillAura.target instanceof EntityPlayer)) {
            return null;
        }
        return CombatTargeting.asValidPlayer(KillAura.target, Double.MAX_VALUE);
    }

    private long getDamageConfirmationTimeoutMs() {
        int ping = 0;
        if (mc.getNetHandler() != null && mc.thePlayer != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null) {
                ping = Math.max(0, info.getResponseTime());
            }
        }

        long pingAdjustedTimeout = ping * 2L + 250L;
        return Math.max(MIN_DAMAGE_CONFIRMATION_TIMEOUT_MS,
                Math.min(MAX_DAMAGE_CONFIRMATION_TIMEOUT_MS, pingAdjustedTimeout));
    }

    private int findBestAttributeWeapon() {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!isSelectedAttributeWeapon(stack)) {
                continue;
            }

            double score = getAttributeWeaponScore(stack);
            if (bestSlot == -1 || score > bestScore) {
                bestSlot = slot;
                bestScore = score;
            }
        }

        return bestSlot;
    }

    private double getAttributeWeaponScore(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof ItemBow) {
            return ItemSortScoring.getBowSelectionScore(stack);
        }
        return ItemSortScoring.getMeleeSelectionScore(stack);
    }

    private boolean isSelectedAttributeWeapon(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) {
            return false;
        }

        Item item = stack.getItem();
        if (swapStick.isToggled()) {
            return item == Items.stick;
        }
        if (swapSword.isToggled()) {
            return item instanceof ItemSword;
        }
        if (swapAxe.isToggled()) {
            return item instanceof ItemAxe;
        }
        if (swapPickaxe.isToggled()) {
            return item instanceof ItemPickaxe;
        }
        if (swapShovel.isToggled()) {
            return item instanceof ItemSpade;
        }
        if (swapHoe.isToggled()) {
            return item instanceof ItemHoe;
        }
        if (swapFishingRod.isToggled()) {
            return item instanceof ItemFishingRod;
        }
        return swapBow.isToggled() && item instanceof ItemBow;
    }

    private boolean hasSelectedAttributeWeapon() {
        for (ButtonSetting weapon : attributeWeapons) {
            if (weapon.isToggled()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAttributeWeaponButton(ButtonSetting button) {
        for (ButtonSetting weapon : attributeWeapons) {
            if (weapon == button) {
                return true;
            }
        }
        return false;
    }

    private int findBestFallbackWeapon() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        ItemStack currentStack = mc.thePlayer.inventory.getStackInSlot(currentSlot);
        int bestSlot = isFallbackWeapon(currentStack) ? currentSlot : -1;
        double bestScore = bestSlot == -1
                ? Double.NEGATIVE_INFINITY
                : ItemSortScoring.getMeleeSelectionScore(currentStack);

        for (int slot = 0; slot < 9; slot++) {
            if (slot == currentSlot) {
                continue;
            }

            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!isFallbackWeapon(stack)) {
                continue;
            }

            double score = ItemSortScoring.getMeleeSelectionScore(stack);
            if (bestSlot == -1 || score > bestScore) {
                bestSlot = slot;
                bestScore = score;
            }
        }

        return bestSlot;
    }

    private boolean isFallbackWeapon(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) {
            return false;
        }

        Item item = stack.getItem();
        if (item == Items.stick) {
            return false;
        }

        return item instanceof ItemSword || item instanceof ItemAxe;
    }

    private void selectSlot(int slot) {
        if (slot == mc.thePlayer.inventory.currentItem) {
            return;
        }

        mc.thePlayer.inventory.currentItem = slot;
        if (mc.playerController instanceof IAccessorPlayerControllerMP) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }

    private void beginSwapCycle(EntityPlayer player) {
        cycleTargetId = player.getEntityId();
        usePrimaryWeapon = false;
        killAuraCycle = ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target == player;
        clearPendingDamage();
    }

    private void clearPendingDamage() {
        pendingDamageTarget = null;
        pendingTargetHurtTime = 0;
        pendingAttackAt = -1L;
    }

    private void resetSwapCycle() {
        cycleTargetId = -1;
        usePrimaryWeapon = false;
        killAuraCycle = false;
        clearPendingDamage();
    }

    private void resetTarget() {
        target = null;
        targetStartTime = 0L;
    }
}
