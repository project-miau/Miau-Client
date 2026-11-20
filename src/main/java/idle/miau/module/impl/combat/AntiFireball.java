package idle.miau.module.impl.combat;

import idle.miau.event.ClientRotationEvent;
import idle.miau.event.PostMotionEvent;
import idle.miau.event.PreUpdateEvent;
import idle.miau.helper.RotationPriority;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.entity.EntityJoinWorldEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.CombatAim;
import idle.miau.utility.PlayerStateTracker;
import idle.miau.utility.PlayerUtils;
import idle.miau.utility.RotationUtils;
import idle.miau.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;

import java.util.HashSet;
import java.util.Set;

public class AntiFireball extends Module {
    private final SliderSetting fov;
    private final SliderSetting range;
    private final Set<EntityLargeFireball> fireballs = new HashSet<>();
    private EntityFireball target;
    private EntityFireball pendingAttack;

    public AntiFireball() {
        super("AntiFireball", category.combat);
        setDescription("Automatically hits nearby enemy fireballs.");
        registerSetting(fov = new SliderSetting("FOV", 360, 20, 360, 5));
        registerSetting(range = new SliderSetting("Range", 10, 3, 20, 0.5));
    }

    @Override
    public void onEnable() {
        clearState();
        if (Utils.nullCheck()) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                track(entity);
            }
        }
    }

    @Subscribe(priority = Priority.HIGH)
    public void onPreUpdate(PreUpdateEvent event) {
        pendingAttack = null;
        if (!Utils.nullCheck() || mc.currentScreen != null) {
            target = null;
            return;
        }
        fireballs.removeIf(fireball -> fireball.isDead || !PlayerUtils.isEntityLoaded(fireball));
        target = findTarget();
        if (target != null && mc.thePlayer.ticksExisted % 2 == 0) {
            pendingAttack = target;
        }
    }

    private EntityFireball findTarget() {
        double rangeSq = range.getInput() * range.getInput();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityLargeFireball && fireballs.contains(entity)
                    && !entity.isDead && entity.canBeCollidedWith()
                    && mc.thePlayer.getDistanceSqToEntity(entity) <= rangeSq
                    && RotationUtils.angleToEntity(entity) <= fov.getInput()) {
                return (EntityFireball) entity;
            }
        }
        return null;
    }

    @Subscribe(priority = Priority.LOW)
    public void onClientRotation(ClientRotationEvent event) {
        if (!Utils.nullCheck() || mc.currentScreen != null) {
            return;
        }
        target = findTarget();
        if (!PlayerUtils.isEntityLoaded(target)) {
            return;
        }
        float[] rotations = CombatAim.rotations(target, null);
        rotations = RotationUtils.fixRotation(rotations[0], rotations[1],
                RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
        if (event.trySetRotation(this, RotationPriority.COMBAT, rotations[0], rotations[1])) {
            event.setRotationAlreadyQuantized(true);
        }
    }

    @Subscribe
    public void onPostMotion(PostMotionEvent event) {
        EntityFireball fireball = pendingAttack;
        pendingAttack = null;
        if (!Utils.nullCheck() || mc.currentScreen != null || mc.thePlayer.isBlocking()
                || !PlayerUtils.isEntityLoaded(fireball) || fireball.isDead
                || mc.thePlayer.getDistanceSqToEntity(fireball) > range.getInput() * range.getInput()
                || PlayerStateTracker.attacking || PlayerStateTracker.digging || PlayerStateTracker.placing) {
            return;
        }
        mc.thePlayer.swingItem();
        mc.playerController.attackEntity(mc.thePlayer, fireball);
    }

    public boolean shouldTakeScaffoldRotation() {
        return isEnabled() && Utils.nullCheck() && mc.currentScreen == null
                && findTarget() != null;
    }

    private void track(Entity entity) {
        if (Utils.nullCheck() && entity instanceof EntityLargeFireball && !Utils.isLobby()
                && ((EntityLargeFireball) entity).shootingEntity != mc.thePlayer
                && mc.thePlayer.getDistanceSqToEntity(entity) > 20.0D) {
            fireballs.add((EntityLargeFireball) entity);
        }
    }

    @Subscribe
    public void onWorldJoin(EntityJoinWorldEvent event) {
        if (event.entity == mc.thePlayer) {
            clearState();
        } else {
            track(event.entity);
        }
    }

    @Subscribe
    public void onWorldLoad(WorldEvent.Load event) {
        clearState();
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        clearState();
    }

    @Override
    public void onDisable() {
        clearState();
    }

    private void clearState() {
        fireballs.clear();
        target = null;
        pendingAttack = null;
    }
}
