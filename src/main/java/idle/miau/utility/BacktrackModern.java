package idle.miau.utility;

import idle.miau.module.impl.player.Backtrack;
import idle.miau.module.impl.render.ArrayListModule;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.WeaponPolicy;
import idle.miau.utility.packet.PacketPipeline;
import idle.miau.utility.AttackInput;
import idle.miau.utility.ClientContext;
import idle.miau.utility.MiauMath;
import idle.miau.utility.SemanticEvent;
import idle.miau.helper.GameplayHelper;
import idle.miau.utility.packet.PacketAccess;
import idle.miau.utility.packet.PredictionGeometry;
import idle.miau.utility.packet.RemoteBoxRenderer;
import idle.miau.utility.packet.TimingAccess;
import idle.miau.utility.rotation.RotationMath;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.util.Vec3;

public final strictfp class BacktrackModern {
   private final WeaponPolicy weapons = WeaponPolicy.SHARED;
   private final Backtrack owner;
   private EntityPlayer target;
   private boolean active;
   private boolean disableOnHit;
   private boolean absolute;
   private boolean hasYaw;
   private boolean render;
   private int targetId;
   private int selfId;
   private int cooldown;
   private int delayedSequence;
   private int releasedSequence;
   private int teleportSequence;
   private int interpolation;
   private long delay;
   private Vec3 offset = zero();
   private Vec3 previous = zero();
   private Vec3 current = zero();
   private Vec3 desired = zero();
   private float delayedYaw;
   private float previousYaw;
   private float currentYaw;
   private float desiredYaw;

   private final SliderSetting minDelay = new SliderSetting("Min Delay", "ms", 100.0D, 10.0D, 1000.0D, 1.0D, "Maximum delay", "setting.014");
   private final SliderSetting maxDelay = new SliderSetting("Max Delay", "ms", 100.0D, 10.0D, 1000.0D, 1.0D, "Maximum delay");
   private final RangeSetting activeRange = new RangeSetting("Active Range", "", 0.0D, 12.0D, 2.0D, 5.0D, 0.1D, "Target distance");
   private final SliderSetting maxHurtTime = new SliderSetting("Maximum hurt time", 10.0D, 1.0D, 10.0D, 1.0D);
   private final SliderSetting cooldownSetting = new SliderSetting("Cooldown", "ms", 250.0D, 0.0D, 5000.0D, 50.0D);
   private final ButtonSetting flushWhenHit = new ButtonSetting("Flush when hit", false, "Disable when hit");
   private final GroupSetting indicator = new GroupSetting("Indicator");
   private final ButtonSetting renderPosition = new ButtonSetting(indicator, "Enabled", false);
   private final SliderSetting colorMode = new SliderSetting(indicator, "Color mode", 1, new String[]{"Custom", "HUD"});
   private final ColorSetting indicatorColor = new ColorSetting(indicator, "Indicator color", 252, 252, 252, 255);
   private final GroupSetting conditions = new GroupSetting("Conditions");
   private final ButtonSetting mousePressed = new ButtonSetting(conditions, "Left Click", false);
   private final ButtonSetting holdingWeapon = new ButtonSetting(conditions, "Holding weapon", false);

   public BacktrackModern(Backtrack owner) {
      this.owner = owner;
      owner.registerSetting(minDelay, "setting.normal.minDelay");
      owner.registerSetting(maxDelay, "setting.014");
      owner.registerSetting(activeRange, "setting.015");
      owner.registerSetting(maxHurtTime, "setting.016");
      owner.registerSetting(cooldownSetting, "setting.017");
      owner.registerSetting(flushWhenHit, "setting.018");
      owner.registerSetting(indicator, "setting.019");
      owner.registerSetting(renderPosition, "setting.020");
      owner.registerSetting(colorMode, "setting.normal.indicatorColorMode");
      owner.registerSetting(indicatorColor, "setting.021");
      owner.registerSetting(conditions, "setting.025");
      owner.registerSetting(mousePressed, "setting.026");
      owner.registerSetting(holdingWeapon, "setting.027");
   }

   private Setting[] settings() {
      return new Setting[] {minDelay, maxDelay, activeRange, maxHurtTime, cooldownSetting,
              flushWhenHit, indicator, renderPosition, colorMode, indicatorColor,
              conditions, mousePressed, holdingWeapon};
   }

   public void setSettingsVisible(boolean visible) {
      for (Setting setting : settings()) {
         setting.setVisible(visible, owner);
      }
      boolean draw = visible && renderPosition.isToggled();
      colorMode.setVisible(draw, owner);
      indicatorColor.setVisible(draw && (int)colorMode.getInput() == 0, owner);
   }

   int getIndicatorColor() {
      return (int)colorMode.getInput() == 1 ? ArrayListModule.getHudColor(0.0D) : indicatorColor.getRGB();
   }

   public synchronized boolean isActive() {
      return active;
   }

   public EntityPlayer getTarget() {
      return active ? target : null;
   }

   public Vec3 getTargetPosition() {
      return active ? desired : null;
   }

   public synchronized int getCurrentDelay() {
      return active ? (int)delay : (int)Math.max(minDelay.getInput(), maxDelay.getInput());
   }

   public String getInfo() {
      return getCurrentDelay() + "ms";
   }

   public synchronized void reset(boolean replay) {
      clear();
      target = null;
      Backtrack.shouldLag = false;
      PacketPipeline.releaseAll(false);
   }

   private float number(String key) {
      return switch (key) {
         case "max_hurt_time" -> (float)maxHurtTime.getInput();
         case "cooldown" -> (float)(cooldownSetting.getInput() / 50.0D);
         default -> throw new IllegalArgumentException(key);
      };
   }

   private boolean flag(String key) {
      return switch (key) {
         case "require_mouse_pressed" -> mousePressed.isToggled();
         case "require_weapon" -> holdingWeapon.isToggled();
         case "flush_on_hit" -> flushWhenHit.isToggled();
         case "render_remote_pos" -> renderPosition.isToggled();
         default -> throw new IllegalArgumentException(key);
      };
   }

   public strictfp boolean listens(int t) {
      return t == 2 || t == 6 || t == 18;
   }

   public strictfp synchronized void onEvent(ClientContext c, SemanticEvent e) {
      if (e.type == 2) {
         this.packet(e);
      } else if (e.type == 6) {
         if (this.render) {
            RemoteBoxRenderer.draw(
               e,
               this.previous,
               this.current,
               this.previousYaw,
               this.currentYaw,
               true,
               true,
               1.0F,
               new java.awt.Color(getIndicatorColor())
            );
         }
      } else if (this.cooldown <= 0 || --this.cooldown == 0) {
         EntityPlayer target = GameplayHelper.target();
         this.target = target;
         float low = (float)activeRange.getMinimum();
         float high = (float)activeRange.getMaximum();
         boolean allowed = c.inputReady()
            && (!this.flag("require_mouse_pressed") || AttackInput.pressed(c))
            && (!this.flag("require_weapon") || this.weapons.isWeapon(c.player()))
            && c.player().ticksExisted - c.player().getLastAttackerTime() <= 20
            && target != null
            && !((float)target.hurtTime > this.number("max_hurt_time"))
            && !((double)(low * low) > GameplayHelper.targetDistanceSquared())
            && !(GameplayHelper.targetDistanceSquared() > (double)(high * high));
         if (!allowed) {
            if (this.active) {
               this.cooldown = MiauMath.truncateInt(this.number("cooldown"));
            }

            this.active = this.render = false;
         } else {
            Vec3 self = TimingAccess.position(c.player());
            Vec3 server = TimingAccess.server(target);
            int oldId = this.targetId;
            this.targetId = target.getEntityId();
            this.selfId = c.player().getEntityId();
            this.disableOnHit = this.flag("flush_on_hit");
            if (!this.active) {
               if (!(0.01 < RotationMath.distance(self, server) - RotationMath.distance(self, TimingAccess.position(target)))) {
                  this.render = false;
               } else {
                  this.rollDelay();
                  this.active = true;
                  this.offset = zero();
                  this.absolute = this.hasYaw = false;
                  this.previous = this.current = this.desired = TimingAccess.position(target);
                  this.previousYaw = this.currentYaw = this.desiredYaw = target.rotationYaw;
                  this.render = this.flag("render_remote_pos");
               }
            } else {
               Vec3 candidate = this.absolute ? this.offset : this.offset.add(server);
               if (!(-0.05 > metric(self, server, candidate)) && oldId == this.targetId) {
                  this.updatePosition(candidate, this.hasYaw ? this.delayedYaw : target.rotationYaw);
               } else {
                  this.offset = zero();
                  this.absolute = this.hasYaw = false;
                  this.active = this.render = false;
                  this.cooldown = MiauMath.truncateInt(this.number("cooldown"));
               }
            }
         }
      }
   }

   private void rollDelay() {
      int minimum = (int)Math.min(minDelay.getInput(), maxDelay.getInput());
      int maximum = (int)Math.max(minDelay.getInput(), maxDelay.getInput());
      this.delay = minimum == maximum
         ? minimum
         : minimum + (int)(Math.random() * (maximum - minimum + 1));
   }

   public static strictfp double metric(Vec3 self, Vec3 server, Vec3 delayed) {
      return RotationMath.distance(self, delayed) - RotationMath.distance(self, server);
   }

   public synchronized strictfp void packet(SemanticEvent e) {
      if (this.active) {
         if (e.packet instanceof S19PacketEntityStatus) {
            if (PacketAccess.statusId((S19PacketEntityStatus)e.packet) == this.targetId) {
               e.dispatchMode = 3;
               return;
            }
         } else if (e.packet instanceof S06PacketUpdateHealth || e.packet instanceof S29PacketSoundEffect) {
            e.dispatchMode = 3;
            return;
         }

         if (e.packet instanceof S12PacketEntityVelocity) {
            if (this.disableOnHit && ((S12PacketEntityVelocity)e.packet).getEntityID() == this.selfId) {
               this.active = false;
               return;
            }
         } else if (e.packet instanceof S08PacketPlayerPosLook) {
            this.active = false;
            return;
         }

         if (e.packetAgeMillis <= this.delay) {
            e.dispatchMode = 1;
            if (e.packetSequence > this.delayedSequence) {
               this.delayedSequence = e.packetSequence;
               this.publish(e, false);
            }
         } else if (e.dispatchMode == 0 && e.packetSequence > this.releasedSequence) {
            this.publish(e, true);
            this.releasedSequence = e.packetSequence;
         }
      }
   }

   private strictfp void publish(SemanticEvent e, boolean reconcile) {
      if (!reconcile || this.teleportSequence <= e.packetSequence) {
         boolean relative = e.packet instanceof S14PacketEntity;
         if (relative || e.packet instanceof S18PacketEntityTeleport) {
            S14PacketEntity move = relative ? (S14PacketEntity)e.packet : null;
            S18PacketEntityTeleport teleport = relative ? null : (S18PacketEntityTeleport)e.packet;
            if ((relative ? PacketAccess.relativeId(move) : teleport.getEntityId()) == this.targetId) {
               Vec3 value = relative
                  ? new Vec3((double)move.func_149062_c() * 0.03125, (double)move.func_149061_d() * 0.03125, (double)move.func_149064_e() * 0.03125)
                  : new Vec3((double)teleport.getX() * 0.03125, (double)teleport.getY() * 0.03125, (double)teleport.getZ() * 0.03125);
               if (reconcile) {
                  this.offset = this.offset.subtract(value);
                  if (!relative) {
                     this.absolute = false;
                  }
               } else {
                  if (relative) {
                     this.offset = this.offset.add(value);
                     if (!move.func_149060_h()) {
                        return;
                     }
                  } else {
                     this.offset = value;
                     this.absolute = true;
                  }

                  this.delayedYaw = (float)(relative ? move.func_149066_f() : teleport.getYaw()) * 360.0F * 0.00390625F;
                  this.hasYaw = true;
                  if (!relative) {
                     this.teleportSequence = e.packetSequence;
                  }
               }
            }
         }
      }
   }

   private strictfp void updatePosition(Vec3 position, float yaw) {
      if (!sameBits(this.desired, position) || yaw != this.desiredYaw) {
         this.interpolation = 3;
      }

      this.desired = position;
      this.previous = this.current;
      this.previousYaw = this.currentYaw;
      this.desiredYaw = yaw;
      if (this.interpolation < 1) {
         this.current = position;
      } else {
         double fraction = 1.0 / (double)this.interpolation;
         this.current = this.current.add(PredictionGeometry.scale(position.subtract(this.current), fraction));
         yaw = (float)((double)RotationMath.wrap(this.desiredYaw - this.currentYaw) * fraction) + this.currentYaw;
         this.interpolation--;
      }

      this.currentYaw = yaw;
   }

   private static strictfp boolean sameBits(Vec3 a, Vec3 b) {
      return Double.doubleToRawLongBits(a.xCoord) == Double.doubleToRawLongBits(b.xCoord)
         && Double.doubleToRawLongBits(a.yCoord) == Double.doubleToRawLongBits(b.yCoord)
         && Double.doubleToRawLongBits(a.zCoord) == Double.doubleToRawLongBits(b.zCoord);
   }

   private static strictfp Vec3 zero() {
      return new Vec3(0.0, 0.0, 0.0);
   }

   public strictfp synchronized void onWorldUnload(ClientContext c) {
      this.clear();
   }

   private strictfp void clear() {
      this.active = this.disableOnHit = this.absolute = this.hasYaw = this.render = false;
      this.targetId = this.selfId = this.cooldown = this.delayedSequence = this.releasedSequence = this.teleportSequence = this.interpolation = 0;
      this.delay = 0L;
      this.offset = this.previous = this.current = this.desired = zero();
      this.delayedYaw = this.previousYaw = this.currentYaw = this.desiredYaw = 0.0F;
   }

}
