package idle.miau.module.impl.player;

import idle.miau.event.AttackEvent;
import idle.miau.event.ClickBlockEvent;
import idle.miau.mcp.accessor.IAccessorPlayerControllerMP;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.ItemUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.inventory.SilentHotbar;
import net.minecraft.block.Block;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;

public final class AutoTool extends Module {
    private static final String[] MODES = {"Silent", "Switch"};

    private final SliderSetting mode;
    private final SliderSetting switchDelay;
    private final ButtonSetting switchBack;
    private final ButtonSetting onlySneaking;
    private final ButtonSetting pauseInCombat;

    private int previewSlot = -1;
    private float animationY = 20.0F;
    private float alpha;
    private long lastShowTime;
    private long lastCombatTime;
    private EntityLivingBase combatTarget;
    private boolean killAuraCombat;
    private int switchBackSlot = -1;
    private BlockPos switchDelayBlock;
    private long switchDelayStartedAt;

    public AutoTool() {
        super("AutoTool", category.player);
        setDescription("Selects the best tool for the block you are mining.");
        registerSetting(mode = new SliderSetting("Mode", 0, MODES));
        registerSetting(switchDelay = new SliderSetting(
                "Switch Delay", "ms", 0.0D, 0.0D, 1000.0D, 25.0D));
        registerSetting(switchBack = new ButtonSetting("SwitchBack", true));
        registerSetting(onlySneaking = new ButtonSetting("OnlySneaking", false));
        registerSetting(pauseInCombat = new ButtonSetting("Pause In Combat", false));
        closetModule = true;
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Override
    public void onDisable() {
        SilentHotbar.INSTANCE.resetSlot(this, true);
        restoreSwitchSlot();
        clearCombatState();
        clearSwitchDelay();
        previewSlot = -1;
        alpha = 0.0F;
        animationY = 20.0F;
    }

    @Subscribe
    public void onGameTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !Utils.nullCheck()) {
            return;
        }
        EntityLivingBase attacker = mc.thePlayer.getLastAttacker();
        if (mc.thePlayer.hurtTime > 0 && attacker != null) {
            combatTarget = attacker;
            killAuraCombat = false;
            lastCombatTime = System.currentTimeMillis();
        }
        if (pauseInCombat.isToggled() && updateCombatState()) {
            SilentHotbar.INSTANCE.resetSlot(this, true);
            restoreSwitchSlot();
            clearSwitchDelay();
            lastShowTime = 0L;
            return;
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            clearSwitchDelay();
        }
        if (!switchBack.isToggled()) {
            switchBackSlot = -1;
            return;
        }
        if (mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        SilentHotbar.INSTANCE.resetSlot(this, false);
        restoreSwitchSlot();
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (!Utils.nullCheck()
                || event.attacker != mc.thePlayer
                || !(event.target instanceof EntityLivingBase)) {
            return;
        }
        combatTarget = (EntityLivingBase) event.target;
        killAuraCombat = getKillAuraTarget() == combatTarget;
        lastCombatTime = System.currentTimeMillis();
        if (pauseInCombat.isToggled()) {
            SilentHotbar.INSTANCE.resetSlot(this, true);
            restoreSwitchSlot();
            clearSwitchDelay();
            lastShowTime = 0L;
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        SilentHotbar.INSTANCE.resetSlot(this, false);
        restoreSwitchSlot();
        clearCombatState();
        clearSwitchDelay();
    }

    @Subscribe
    public void onClickBlock(ClickBlockEvent event) {
        if (!Utils.nullCheck() || event.clickedBlock == null) {
            return;
        }
        if (pauseInCombat.isToggled() && updateCombatState()) {
            SilentHotbar.INSTANCE.resetSlot(this, true);
            restoreSwitchSlot();
            clearSwitchDelay();
            lastShowTime = 0L;
            return;
        }
        Block block = mc.theWorld.getBlockState(event.clickedBlock).getBlock();
        if (onlySneaking.isToggled() && !mc.thePlayer.isSneaking()
                || block.getBlockHardness(mc.theWorld, event.clickedBlock) == 0.0F) {
            clearSwitchDelay();
            return;
        }

        int currentSlot = mc.thePlayer.inventory.currentItem;
        int fastestSlot = findBestToolSlot(block, currentSlot);
        if (fastestSlot == currentSlot) {
            clearSwitchDelay();
            return;
        }
        if (!hasSwitchDelayElapsed(event.clickedBlock, System.currentTimeMillis())) {
            return;
        }

        if ((int) mode.getInput() == 0) {
            SilentHotbar.INSTANCE.selectSlotSilently(
                    this, fastestSlot, null, false, false, true);
            previewSlot = fastestSlot;
            lastShowTime = System.currentTimeMillis();
        } else {
            if (switchBack.isToggled() && switchBackSlot == -1) {
                switchBackSlot = mc.thePlayer.inventory.currentItem;
            }
            mc.thePlayer.inventory.currentItem = fastestSlot;
            syncCurrentItem();
        }
        clearSwitchDelay();
    }

    public static int findBestToolSlot(Block block, int currentSlot) {
        if (mc == null || mc.thePlayer == null || block == null) return currentSlot;
        int bestSlot = currentSlot >= 0 && currentSlot < 9 ? currentSlot : 0;
        ItemStack bestStack = mc.thePlayer.inventory.getStackInSlot(bestSlot);
        float bestSpeed = ItemUtils.getToolEfficiency(bestStack, block);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            float speed = ItemUtils.getToolEfficiency(stack, block);
            if (speed > bestSpeed) {
                bestSlot = slot;
                bestSpeed = speed;
            }
        }
        return bestSlot;
    }

    @Subscribe
    public void onRender2D(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || (int) mode.getInput() != 0 || !Utils.nullCheck()) {
            return;
        }
        boolean visible = previewSlot >= 0 && previewSlot < 9
                && System.currentTimeMillis() - lastShowTime <= 1000L;
        float targetY = visible ? 0.0F : 20.0F;
        float targetAlpha = visible ? 1.0F : 0.0F;
        animationY += (targetY - animationY) * 0.15F;
        alpha += (targetAlpha - alpha) * 0.15F;
        if (alpha <= 0.01F) {
            previewSlot = -1;
            return;
        }

        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(previewSlot);
        if (stack == null) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        int x = (int) (resolution.getScaledWidth() / 2.0F - 8.0F);
        int y = (int) (resolution.getScaledHeight() - 72.0F + animationY);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public int getPreviewSlot() {
        return previewSlot;
    }

    public boolean shouldRenderPreview() {
        return (int) mode.getInput() == 0 && previewSlot >= 0 && previewSlot < 9;
    }

    private boolean updateCombatState() {
        if (mc.thePlayer == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean killAuraEnabled = ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled();
        EntityLivingBase killAuraTarget = getKillAuraTarget();
        EntityLivingBase activeTarget = resolveCombatTarget(
                combatTarget, killAuraCombat, killAuraEnabled, killAuraTarget);
        if (activeTarget == null) {
            clearCombatState();
            return false;
        }
        combatTarget = activeTarget;
        if (killAuraTarget != null) {
            killAuraCombat = true;
            lastCombatTime = now;
            return true;
        }
        if (combatTarget.hurtTime > 0) {
            lastCombatTime = now;
            return true;
        }
        if (now - lastCombatTime < 250L) {
            return true;
        }
        if (combatTarget.isDead || mc.thePlayer.getDistanceToEntity(combatTarget) > 7.0F) {
            clearCombatState();
            return false;
        }
        return true;
    }

    static <T> T resolveCombatTarget(
            T trackedTarget,
            boolean trackedByKillAura,
            boolean killAuraEnabled,
            T killAuraTarget) {
        if (killAuraEnabled) {
            return killAuraTarget;
        }
        return trackedByKillAura ? null : trackedTarget;
    }

    private EntityLivingBase getKillAuraTarget() {
        return ModuleManager.killAura != null && ModuleManager.killAura.isEnabled()
                ? ModuleManager.killAura.getTarget()
                : null;
    }

    private void clearCombatState() {
        combatTarget = null;
        killAuraCombat = false;
        lastCombatTime = 0L;
    }

    boolean hasSwitchDelayElapsed(BlockPos blockPos, long now) {
        long delay = Math.max(0L, Math.round(switchDelay.getInput()));
        if (delay == 0L) {
            clearSwitchDelay();
            return true;
        }
        if (!blockPos.equals(switchDelayBlock)) {
            switchDelayBlock = blockPos;
            switchDelayStartedAt = now;
            return false;
        }
        return now - switchDelayStartedAt >= delay;
    }

    private void clearSwitchDelay() {
        switchDelayBlock = null;
        switchDelayStartedAt = 0L;
    }

    private void restoreSwitchSlot() {
        if (switchBackSlot < 0 || mc.thePlayer == null) {
            switchBackSlot = -1;
            return;
        }
        mc.thePlayer.inventory.currentItem = switchBackSlot;
        switchBackSlot = -1;
        syncCurrentItem();
    }

    private void syncCurrentItem() {
        if (mc.playerController instanceof IAccessorPlayerControllerMP) {
            ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
        }
    }
}
