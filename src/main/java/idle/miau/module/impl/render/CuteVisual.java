package idle.miau.module.impl.render;

import idle.miau.performance.MiauDrawCalls;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import idle.miau.event.PreUpdateEvent;
import idle.miau.event.SendPacketEvent;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.SliderSetting;

import java.util.Random;

public final class CuteVisual extends Module {
    private final DescriptionSetting authorDescription = new DescriptionSetting("dev. frail & chef");
    public final ButtonSetting bedSound = new ButtonSetting("Bed Sound", true, "setting.001");
    private final DescriptionSetting bedBreakDescription = new DescriptionSetting("Bed break");
    public final ButtonSetting bedBurst = new ButtonSetting("Bed Burst", true, "setting.003");
    public final SliderSetting bedBurstCount = new SliderSetting("Bed Burst Count", "", 20, 5, 40, 1, "setting.004");
    public final SliderSetting bedBurstSize = new SliderSetting("Bed Burst Size", "", 0.20, 0.05, 0.6, 0.01, "setting.005");
    public final SliderSetting bedBurstSpeed = new SliderSetting("Bed Burst Speed", "", 2.5, 0.5, 7.0, 0.1, "setting.006");
    public final SliderSetting bedBurstLifetime = new SliderSetting("Bed Burst Lifetime", "ms", 1500, 500, 3000, 100, "setting.007");
    public final ButtonSetting rainbow = new ButtonSetting("Rainbow", true, "setting.008");
    public final SliderSetting rainbowLineWidth = new SliderSetting("Rainbow Line Width", "", 5.0, 1.0, 12.0, 0.5, "setting.009");
    public final SliderSetting rainbowDuration = new SliderSetting("Rainbow Duration", "ms", 3000, 1000, 6000, 200, "setting.010");
    private final DescriptionSetting trailDescription = new DescriptionSetting("Trail");
    public final ButtonSetting onlyWhileMoving = new ButtonSetting("Only While Moving", true, "setting.012");
    public final SliderSetting opacity = new SliderSetting("Opacity", "%", 85, 20, 100, 5, "setting.013");
    public final ButtonSetting hearts = new ButtonSetting("Hearts", true, "setting.014");
    public final SliderSetting heartsSpawnRate = new SliderSetting("Hearts Spawn Rate", "ms", 200, 50, 500, 10, "setting.015");
    public final SliderSetting heartsLifetime = new SliderSetting("Hearts Lifetime", "ms", 1500, 500, 4000, 100, "setting.016");
    public final ButtonSetting dots = new ButtonSetting("Dots", true, "setting.017");
    public final SliderSetting dotsSpawnRate = new SliderSetting("Dots Spawn Rate", "ms", 100, 20, 200, 10, "setting.018");
    public final SliderSetting dotsLifetime = new SliderSetting("Dots Lifetime", "ms", 1500, 500, 5000, 100, "setting.019");
    public final ButtonSetting pulse = new ButtonSetting("Pulse", false, "setting.020");

    private static final NativeGl gl = new NativeGl();

    public CuteVisual() {
        super("CuteVisual", category.render);
        registerSetting(authorDescription);
        registerSetting(bedSound);
        registerSetting(bedBreakDescription);
        registerSetting(bedBurst);
        registerSetting(bedBurstCount);
        registerSetting(bedBurstSize);
        registerSetting(bedBurstSpeed);
        registerSetting(bedBurstLifetime);
        registerSetting(rainbow);
        registerSetting(rainbowLineWidth);
        registerSetting(rainbowDuration);
        registerSetting(trailDescription);
        registerSetting(onlyWhileMoving);
        registerSetting(opacity);
        registerSetting(hearts);
        registerSetting(heartsSpawnRate);
        registerSetting(heartsLifetime);
        registerSetting(dots);
        registerSetting(dotsSpawnRate);
        registerSetting(dotsLifetime);
        registerSetting(pulse);
        initializeGeometry();
    }

int MAX_HEARTS = 50;
int MAX_DOTS = 100;
int MAX_BED_PARTICLES = 200;
int MAX_RAINBOWS = 5;

int TRAIL_HEART_SEGMENTS = 30;
int BED_HEART_SEGMENTS = 20;
int DOT_SEGMENTS = 8;
int DOT_FILL_SEGMENTS = 6;
int RAINBOW_SEGMENTS = 30;

double TWO_PI = Math.PI * 2.0;

double HEART_RADIUS = 1.5;
double HEART_SIZE = 0.15;
double HEART_FLOAT_SPEED = 1.0;
float HEART_LINE_WIDTH = 1.5f;

double DOT_SIZE = 0.04;
double DOT_SPREAD = 0.9;
double DOT_DRIFT_SPEED = 0.3;
float DOT_LINE_WIDTH = 2.0f;
int DOT_GLOW_LAYERS = 1;

double RAINBOW_SIZE = 3.0;
double RAINBOW_BAND_WIDTH = 0.15;
boolean RAINBOW_GLOW = true;

Random random = new Random();

double[] heartX = new double[50];
double[] heartY = new double[50];
double[] heartZ = new double[50];
long[] heartTime = new long[50];
float[] heartRotY = new float[50];
float[] heartRotZ = new float[50];
float[] heartScale = new float[50];
int[] heartType = new int[50];
boolean[] heartActive = new boolean[50];
int activeHeartCount = 0;
long lastHeartSpawn = 0;

double[] dotX = new double[100];
double[] dotY = new double[100];
double[] dotZ = new double[100];
double[] dotDriftX = new double[100];
double[] dotDriftY = new double[100];
double[] dotDriftZ = new double[100];
long[] dotTime = new long[100];
float[] dotScale = new float[100];
int[] dotType = new int[100];
boolean[] dotActive = new boolean[100];
int activeDotCount = 0;
long lastDotSpawn = 0;
double lastDotPosX = 0;
double lastDotPosY = 0;
double lastDotPosZ = 0;
boolean hasLastDotPos = false;

double[] bedParticleX = new double[200];
double[] bedParticleY = new double[200];
double[] bedParticleZ = new double[200];
double[] bedParticleVX = new double[200];
double[] bedParticleVY = new double[200];
double[] bedParticleVZ = new double[200];
float[] bedParticleScale = new float[200];
int[] bedParticleType = new int[200];
long[] bedParticleTime = new long[200];
boolean[] bedParticleActive = new boolean[200];
int activeBedParticleCount = 0;

double[] rainbowX = new double[5];
double[] rainbowY = new double[5];
double[] rainbowZ = new double[5];
float[] rainbowYaw = new float[5];
long[] rainbowTime = new long[5];
boolean[] rainbowActive = new boolean[5];
int activeRainbowCount = 0;

boolean diggingBed = false;
double bedX = 0;
double bedY = 0;
double bedZ = 0;

double[] trailHeartShapeX = new double[31];
double[] trailHeartShapeY = new double[31];
double[] bedHeartShapeX = new double[21];
double[] bedHeartShapeY = new double[21];
double[] dotCircleX = new double[9];
double[] dotCircleY = new double[9];
double[] dotFillX = new double[7];
double[] dotFillY = new double[7];
double[] starShapeX = new double[9];
double[] starShapeY = new double[9];
double[] rainbowArcCos = new double[31];
double[] rainbowArcSin = new double[31];

double[] rainbowRed =   {0.85, 0.60, 0.50, 0.50, 1.00, 1.00, 1.00};
double[] rainbowGreen = {0.50, 0.50, 0.75, 1.00, 0.90, 0.60, 0.40};
double[] rainbowBlue =  {1.00, 1.00, 1.00, 0.65, 0.50, 0.40, 0.50};

double[] bedParticleRed =   {1.00, 1.00, 1.00, 0.50, 0.50, 0.60, 0.85};
double[] bedParticleGreen = {0.40, 0.60, 0.90, 1.00, 0.75, 0.50, 0.50};
double[] bedParticleBlue =  {0.50, 0.40, 0.50, 0.65, 1.00, 1.00, 1.00};

void initializeGeometry() {
    for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
        double t = (double) i / TRAIL_HEART_SEGMENTS * TWO_PI;
        double sinT = Math.sin(t);
        trailHeartShapeX[i] = 16.0 * sinT * sinT * sinT;
        trailHeartShapeY[i] = 13.0 * Math.cos(t)
            - 5.0 * Math.cos(2.0 * t)
            - 2.0 * Math.cos(3.0 * t)
            - Math.cos(4.0 * t);
    }

    for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
        double t = (double) i / BED_HEART_SEGMENTS * TWO_PI;
        double sinT = Math.sin(t);
        bedHeartShapeX[i] = 16.0 * sinT * sinT * sinT;
        bedHeartShapeY[i] = 13.0 * Math.cos(t)
            - 5.0 * Math.cos(2.0 * t)
            - 2.0 * Math.cos(3.0 * t)
            - Math.cos(4.0 * t);
    }

    for (int i = 0; i <= DOT_SEGMENTS; i++) {
        double angle = (double) i / DOT_SEGMENTS * TWO_PI;
        dotCircleX[i] = Math.cos(angle);
        dotCircleY[i] = Math.sin(angle);
    }

    for (int i = 0; i <= DOT_FILL_SEGMENTS; i++) {
        double angle = (double) i / DOT_FILL_SEGMENTS * TWO_PI;
        dotFillX[i] = Math.cos(angle);
        dotFillY[i] = Math.sin(angle);
    }

    for (int i = 0; i <= 8; i++) {
        double angle = i * Math.PI / 4.0 - Math.PI / 2.0;
        double radius = (i % 2 == 0) ? 12.0 : 5.0;
        starShapeX[i] = Math.cos(angle) * radius;
        starShapeY[i] = Math.sin(angle) * radius;
    }
}

@Override
public void onEnable() {
    resetEffects();
}

@Override
public void onDisable() {
    resetEffects();
}

@Subscribe
public void onWorldUnload(WorldEvent.Unload event) {
    resetEffects();
}

private void resetEffects() {
    clearHearts();
    clearDots();
    clearBedParticles();
    clearRainbows();

    lastHeartSpawn = 0;
    lastDotSpawn = 0;
    hasLastDotPos = false;
    diggingBed = false;
}

void clearHearts() {
    for (int i = 0; i < MAX_HEARTS; i++) heartActive[i] = false;
    activeHeartCount = 0;
}

void clearDots() {
    for (int i = 0; i < MAX_DOTS; i++) dotActive[i] = false;
    activeDotCount = 0;
}

void clearBedParticles() {
    for (int i = 0; i < MAX_BED_PARTICLES; i++) bedParticleActive[i] = false;
    activeBedParticleCount = 0;
}

void clearRainbows() {
    for (int i = 0; i < MAX_RAINBOWS; i++) rainbowActive[i] = false;
    activeRainbowCount = 0;
}

int findFreeHeartSlot() {
    for (int i = 0; i < MAX_HEARTS; i++) {
        if (!heartActive[i]) return i;
    }

    long oldest = Long.MAX_VALUE;
    int oldestIndex = 0;
    for (int i = 0; i < MAX_HEARTS; i++) {
        if (heartTime[i] < oldest) {
            oldest = heartTime[i];
            oldestIndex = i;
        }
    }
    return oldestIndex;
}

int findFreeDotSlot() {
    for (int i = 0; i < MAX_DOTS; i++) {
        if (!dotActive[i]) return i;
    }

    long oldest = Long.MAX_VALUE;
    int oldestIndex = 0;
    for (int i = 0; i < MAX_DOTS; i++) {
        if (dotTime[i] < oldest) {
            oldest = dotTime[i];
            oldestIndex = i;
        }
    }
    return oldestIndex;
}

int findFreeBedParticleSlot() {
    for (int i = 0; i < MAX_BED_PARTICLES; i++) {
        if (!bedParticleActive[i]) return i;
    }

    long oldest = Long.MAX_VALUE;
    int oldestIndex = 0;
    for (int i = 0; i < MAX_BED_PARTICLES; i++) {
        if (bedParticleTime[i] < oldest) {
            oldest = bedParticleTime[i];
            oldestIndex = i;
        }
    }
    return oldestIndex;
}

int findFreeRainbowSlot() {
    for (int i = 0; i < MAX_RAINBOWS; i++) {
        if (!rainbowActive[i]) return i;
    }

    long oldest = Long.MAX_VALUE;
    int oldestIndex = 0;
    for (int i = 0; i < MAX_RAINBOWS; i++) {
        if (rainbowTime[i] < oldest) {
            oldest = rainbowTime[i];
            oldestIndex = i;
        }
    }
    return oldestIndex;
}

@Subscribe
public void onPreUpdate(PreUpdateEvent event) {
    EntityPlayerSP player = mc.thePlayer;
    if (player == null) return;

    double positionX = player.posX;
    double positionY = player.posY;
    double positionZ = player.posZ;

    boolean heartsEnabled = hearts.isToggled();
    boolean dotsEnabled = dots.isToggled();

    if (!heartsEnabled && activeHeartCount > 0) clearHearts();
    if (!dotsEnabled && activeDotCount > 0) clearDots();

    boolean canSpawnDots = hasLastDotPos;
    if (!hasLastDotPos) {
        lastDotPosX = positionX;
        lastDotPosY = positionY;
        lastDotPosZ = positionZ;
        hasLastDotPos = true;
    }

    if (!heartsEnabled && !dotsEnabled) {
        lastDotPosX = positionX;
        lastDotPosY = positionY;
        lastDotPosZ = positionZ;
        return;
    }

    if (onlyWhileMoving.isToggled()
            && Math.abs(player.movementInput.moveForward) < 1.0E-4F
            && Math.abs(player.movementInput.moveStrafe) < 1.0E-4F) return;

    long now = System.currentTimeMillis();

    if (heartsEnabled) {
        long heartRate = (long) heartsSpawnRate.getInput();
        if (now - lastHeartSpawn >= heartRate) {
            lastHeartSpawn = now;
            spawnHeart(positionX, positionY + 0.5, positionZ, now);
        }
    }

    if (dotsEnabled && canSpawnDots) {
        long dotRate = (long) dotsSpawnRate.getInput();
        if (now - lastDotSpawn >= dotRate) {
            lastDotSpawn = now;
            spawnDot(positionY, now);
        }
    }

    lastDotPosX = positionX;
    lastDotPosY = positionY;
    lastDotPosZ = positionZ;
}

void spawnHeart(double x, double y, double z, long now) {
    int slot = findFreeHeartSlot();
    boolean wasActive = heartActive[slot];

    double angle = random.nextDouble() * TWO_PI;
    double distance = random.nextDouble() * HEART_RADIUS;

    heartX[slot] = x + Math.cos(angle) * distance;
    heartZ[slot] = z + Math.sin(angle) * distance;
    heartY[slot] = y + random.nextDouble() * 0.5;
    heartTime[slot] = now;
    heartRotY[slot] = (float) (random.nextDouble() * 360.0);
    heartRotZ[slot] = (float) (random.nextDouble() * 30.0 - 15.0);
    heartScale[slot] = (float) (HEART_SIZE * (0.6 + random.nextDouble() * 0.8));
    heartType[slot] = random.nextInt(3);
    heartActive[slot] = true;

    if (!wasActive) activeHeartCount++;
}

void spawnDot(double positionY, long now) {
    int slot = findFreeDotSlot();
    boolean wasActive = dotActive[slot];

    dotX[slot] = lastDotPosX + (random.nextDouble() - 0.5) * DOT_SPREAD;
    dotY[slot] = positionY + 0.3 + random.nextDouble() * 1.2;
    dotZ[slot] = lastDotPosZ + (random.nextDouble() - 0.5) * DOT_SPREAD;

    dotDriftX[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;
    dotDriftY[slot] = (0.3 + random.nextDouble() * 0.7) * DOT_DRIFT_SPEED;
    dotDriftZ[slot] = (random.nextDouble() - 0.5) * DOT_DRIFT_SPEED;

    dotTime[slot] = now;
    dotScale[slot] = (float) (DOT_SIZE * (0.5 + random.nextDouble()));
    dotType[slot] = random.nextInt(4);
    dotActive[slot] = true;

    if (!wasActive) activeDotCount++;
}

@Subscribe
public void onPacketSent(SendPacketEvent event) {
    if (!(event.getPacket() instanceof C07PacketPlayerDigging) || mc.theWorld == null) return;

    C07PacketPlayerDigging diggingPacket = (C07PacketPlayerDigging) event.getPacket();
    C07PacketPlayerDigging.Action status = diggingPacket.getStatus();
    BlockPos position = diggingPacket.getPosition();
    if (status == null || position == null) return;

    if (status == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK) {
        Block block = mc.theWorld.getBlockState(position).getBlock();
        if (!(block instanceof BlockBed)) {
            diggingBed = false;
            return;
        }

        double x = position.getX() + 0.5;
        double y = position.getY() + 0.5;
        double z = position.getZ() + 0.5;

        if (mc.playerController != null && mc.playerController.isInCreativeMode()) {
            diggingBed = false;
            spawnBedBreak(x, y, z);
        } else {
            diggingBed = true;
            bedX = x;
            bedY = y;
            bedZ = z;
        }
    } else if (status == C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK) {
        if (diggingBed) {
            spawnBedBreak(bedX, bedY, bedZ);
            diggingBed = false;
        }
    } else if (status == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK) {
        diggingBed = false;
    }
}

void spawnBedBreak(double x, double y, double z) {
    long now = System.currentTimeMillis();

    if (rainbow.isToggled()) {
        int slot = findFreeRainbowSlot();
        boolean wasActive = rainbowActive[slot];

        rainbowX[slot] = x;
        rainbowY[slot] = y;
        rainbowZ[slot] = z;
        rainbowTime[slot] = now;
        rainbowActive[slot] = true;

        EntityPlayerSP player = mc.thePlayer;
        if (player != null) {
            rainbowYaw[slot] = (float) Math.toDegrees(
                Math.atan2(player.posX - x, player.posZ - z)
            );
        } else {
            rainbowYaw[slot] = 0;
        }

        if (!wasActive) activeRainbowCount++;
    }

    if (bedBurst.isToggled()) {
        double speed = bedBurstSpeed.getInput();
        double baseSize = bedBurstSize.getInput();
        int count = (int) bedBurstCount.getInput();

        for (int i = 0; i < count; i++) {
            int slot = findFreeBedParticleSlot();
            boolean wasActive = bedParticleActive[slot];

            bedParticleX[slot] = x;
            bedParticleY[slot] = y;
            bedParticleZ[slot] = z;

            double theta = random.nextDouble() * TWO_PI;
            double phi = random.nextDouble() * Math.PI * 0.67 - Math.PI / 6.0;
            double particleSpeed = (0.8 + random.nextDouble() * 1.2) * speed;
            double cosPhi = Math.cos(phi);

            bedParticleVX[slot] = cosPhi * Math.cos(theta) * particleSpeed;
            bedParticleVY[slot] = Math.sin(phi) * particleSpeed + 1.0;
            bedParticleVZ[slot] = cosPhi * Math.sin(theta) * particleSpeed;

            int typeRoll = random.nextInt(5);
            bedParticleType[slot] = typeRoll < 2 ? 0 : typeRoll - 1;
            bedParticleScale[slot] = (float) (baseSize * (0.6 + random.nextDouble() * 0.8));
            bedParticleTime[slot] = now;
            bedParticleActive[slot] = true;

            if (!wasActive) activeBedParticleCount++;
        }
    }

    if (bedSound.isToggled() && mc.thePlayer != null) {
        mc.thePlayer.playSound("random.orb", 1.0f, 1.5f);
        mc.thePlayer.playSound("random.levelup", 0.5f, 2.0f);
    }
}

@Subscribe
public void onRenderWorld(RenderWorldLastEvent event) {
    if (activeHeartCount <= 0
        && activeDotCount <= 0
        && activeBedParticleCount <= 0
        && activeRainbowCount <= 0) return;
    if (mc.thePlayer == null || mc.getRenderManager() == null) return;

    Vec3 camera = new Vec3(
            mc.getRenderManager().viewerPosX,
            mc.getRenderManager().viewerPosY,
            mc.getRenderManager().viewerPosZ);
    long now = System.currentTimeMillis();
    gl.pushState();
    try {
        renderTrail(camera, now);
        renderBedVisuals(camera, now);
    } finally {
        gl.popState();
    }
}

void renderTrail(Vec3 camera, long now) {
    boolean renderHearts = activeHeartCount > 0 && hearts.isToggled();
    boolean renderDots = activeDotCount > 0 && dots.isToggled();
    if (!renderHearts && !renderDots) return;

    double opacityValue = opacity.getInput() / 100.0;

    gl.push();
    try {
        gl.texture2d(false);
        gl.depthMask(false);
        gl.blend(true);
        gl.cull(false);

        if (renderHearts) renderHearts(camera, now, opacityValue);
        if (renderDots) renderDots(camera, now, opacityValue);

        gl.cull(true);
        gl.depthMask(true);
        gl.texture2d(true);
        gl.blend(false);
        gl.resetColor();
    } finally {
        gl.pop();
    }
}

void renderHearts(Vec3 camera, long now, double opacity) {
    long lifetime = (long) heartsLifetime.getInput();
    float effectiveLineWidth = Math.max(HEART_LINE_WIDTH * 0.6f, 1.0f);
    gl.lineWidth(effectiveLineWidth);

    for (int i = 0; i < MAX_HEARTS; i++) {
        if (!heartActive[i]) continue;

        long age = now - heartTime[i];
        if (age > lifetime) {
            heartActive[i] = false;
            activeHeartCount--;
            continue;
        }

        double progress = (double) age / (double) lifetime;
        double floatY = HEART_FLOAT_SPEED * progress * 1.5;

        double alpha;
        if (progress < 0.1) alpha = progress / 0.1;
        else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
        else alpha = 1.0;
        alpha *= opacity;

        double scaleAnimation;
        if (progress < 0.1) scaleAnimation = progress / 0.1;
        else if (progress > 0.8) scaleAnimation = (1.0 - progress) / 0.2;
        else scaleAnimation = 1.0;

        double drawX = heartX[i] + Math.sin(age * 0.002 + i * 1.7) * 0.1 - camera.xCoord;
        double drawY = heartY[i] + floatY - camera.yCoord;
        double drawZ = heartZ[i] + Math.cos(age * 0.0015 + i * 2.3) * 0.1 - camera.zCoord;

        double red;
        double green;
        double blue;
        int type = heartType[i];
        if (type == 0) {
            red = 1.0; green = 0.5; blue = 0.8;
        } else if (type == 1) {
            red = 1.0; green = 0.3; blue = 0.6;
        } else {
            red = 0.9; green = 0.4; blue = 0.9;
        }

        double size = heartScale[i] * scaleAnimation;
        double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));
        double spinAngle = (age * 0.1 + heartRotY[i]) % 360.0;

        gl.push();
        try {
            gl.translate(drawX, drawY, drawZ);
            gl.rotate(billboardYaw, 0, 1, 0);
            gl.rotate(spinAngle, 0, 1, 0);
            gl.rotate(heartRotZ[i], 0, 0, 1);
            gl.lineWidth(effectiveLineWidth + 3.0f);
            drawTrailHeart(size, alpha, red, green, blue);
            gl.lineWidth(effectiveLineWidth);
        } finally {
            gl.pop();
        }
    }
}

void drawTrailHeart(double size, double baseAlpha, double red, double green, double blue) {
    double scale = size / 16.0;

    for (int layer = 3; layer >= 0; layer--) {
        double glowScale = scale * (1.0 + layer * 0.08);
        double layerAlpha = layer == 0
            ? baseAlpha * 0.9
            : baseAlpha * (0.3 / layer);

        gl.color(red, green, blue, layerAlpha);
        gl.begin(3);
        for (int i = 0; i <= TRAIL_HEART_SEGMENTS; i++) {
            gl.vertex3(
                trailHeartShapeX[i] * glowScale,
                trailHeartShapeY[i] * glowScale,
                0
            );
        }
        gl.end();
    }
}

void renderDots(Vec3 camera, long now, double opacity) {
    long lifetime = (long) dotsLifetime.getInput();
    boolean pulseEnabled = pulse.isToggled();
    gl.lineWidth(DOT_LINE_WIDTH);

    for (int i = 0; i < MAX_DOTS; i++) {
        if (!dotActive[i]) continue;

        long age = now - dotTime[i];
        if (age > lifetime) {
            dotActive[i] = false;
            activeDotCount--;
            continue;
        }

        double progress = (double) age / (double) lifetime;

        double fade;
        if (progress < 0.1) fade = progress / 0.1;
        else if (progress > 0.5) fade = (1.0 - progress) / 0.5;
        else fade = 1.0;

        double pulseFactor = 1.0;
        if (pulseEnabled) {
            double flickerSpeed = 3.0 + dotType[i] * 1.5;
            pulseFactor = 0.5 + 0.5 * Math.sin(age * 0.01 * flickerSpeed + i * 2.7);
        }

        double ageSeconds = age / 1000.0;
        double drawX = dotX[i]
            + dotDriftX[i] * ageSeconds
            + Math.sin(ageSeconds * 1.5 + i * 1.3) * 0.15
            - camera.xCoord;
        double drawY = dotY[i] + dotDriftY[i] * ageSeconds - camera.yCoord;
        double drawZ = dotZ[i]
            + dotDriftZ[i] * ageSeconds
            + Math.cos(ageSeconds * 1.2 + i * 2.1) * 0.15
            - camera.zCoord;

        double red;
        double green;
        double blue;
        int type = dotType[i];
        if (type == 0) {
            red = 1.0; green = 0.45; blue = 0.7;
        } else if (type == 1) {
            red = 1.0; green = 0.6; blue = 0.85;
        } else if (type == 2) {
            red = 1.0; green = 0.3; blue = 0.55;
        } else {
            red = 1.0; green = 0.75; blue = 0.95;
        }

        double alpha = fade * pulseFactor * opacity;
        if (alpha < 0.02) continue;

        double size = dotScale[i];
        double billboardYaw = Math.toDegrees(Math.atan2(-drawX, -drawZ));

        gl.push();
        try {
            gl.translate(drawX, drawY, drawZ);
            gl.rotate(billboardYaw, 0, 1, 0);
            gl.color(red, green, blue, alpha);

            if (DOT_GLOW_LAYERS == 1) {
                gl.begin(6);
                gl.vertex3(0, 0, 0);
                for (int j = 0; j <= DOT_FILL_SEGMENTS; j++) {
                    gl.vertex3(dotFillX[j] * size, dotFillY[j] * size, 0);
                }
                gl.end();

                gl.begin(3);
                for (int j = 0; j <= DOT_SEGMENTS; j++) {
                    gl.vertex3(dotCircleX[j] * size, dotCircleY[j] * size, 0);
                }
                gl.end();
            }
        } finally {
            gl.pop();
        }
    }
}

void renderBedVisuals(Vec3 camera, long now) {
    if (activeBedParticleCount <= 0 && activeRainbowCount <= 0) return;

    long bedLifetime = activeBedParticleCount > 0
        ? (long) bedBurstLifetime.getInput()
        : 0;
    long rainbowDurationValue = activeRainbowCount > 0
        ? (long) rainbowDuration.getInput()
        : 0;
    float rainbowLineWidthValue = (float) rainbowLineWidth.getInput();

    gl.push();
    try {
        gl.texture2d(false);
        gl.depth(false);
        gl.depthMask(false);
        gl.blend(true);
        gl.lineSmooth(true);
        gl.cull(false);

        if (activeRainbowCount > 0) {
            renderRainbows(camera, now, rainbowDurationValue, rainbowLineWidthValue);
        }

        if (activeBedParticleCount > 0) {
            renderBedParticles(camera, now, bedLifetime, rainbowLineWidthValue);
        }

        gl.lineSmooth(false);
        gl.cull(true);
        gl.depth(true);
        gl.depthMask(true);
        gl.texture2d(true);
        gl.blend(false);
        gl.resetColor();
    } finally {
        gl.pop();
    }
}

void renderRainbows(Vec3 camera, long now, long duration, float lineWidth) {
    for (int rainbow = 0; rainbow < MAX_RAINBOWS; rainbow++) {
        if (!rainbowActive[rainbow]) continue;

        long age = now - rainbowTime[rainbow];
        if (age > duration) {
            rainbowActive[rainbow] = false;
            activeRainbowCount--;
            continue;
        }

        double progress = (double) age / (double) duration;

        double alpha;
        if (progress < 0.15) alpha = progress / 0.15;
        else if (progress > 0.6) alpha = (1.0 - progress) / 0.4;
        else alpha = 1.0;

        double arcProgress = progress < 0.2
            ? Math.pow(progress / 0.2, 2.0)
            : 1.0;

        for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
            double angle = (double) segment / RAINBOW_SEGMENTS * Math.PI * arcProgress;
            rainbowArcCos[segment] = Math.cos(angle);
            rainbowArcSin[segment] = Math.sin(angle);
        }

        double drawX = rainbowX[rainbow] - camera.xCoord;
        double drawY = rainbowY[rainbow] - camera.yCoord;
        double drawZ = rainbowZ[rainbow] - camera.zCoord;

        gl.push();
        try {
            gl.translate(drawX, drawY, drawZ);
            gl.rotate(rainbowYaw[rainbow], 0, 1, 0);

            for (int band = 0; band < 7; band++) {
                double radius = RAINBOW_SIZE + (band - 3) * RAINBOW_BAND_WIDTH;
                if (radius < 0.1) continue;

                double red = rainbowRed[band];
                double green = rainbowGreen[band];
                double blue = rainbowBlue[band];

                if (RAINBOW_GLOW) {
                    drawRainbowArc(radius, red, green, blue, alpha * 0.15, lineWidth + 3.0f);
                    drawRainbowArc(radius, red, green, blue, alpha * 0.30, lineWidth + 1.5f);
                }
                drawRainbowArc(radius, red, green, blue, alpha * 0.85, lineWidth);
            }

            if (arcProgress > 0.5) {
                drawRainbowSparkles(now, arcProgress, alpha);
            }
        } finally {
            gl.pop();
        }
    }
}

void drawRainbowArc(
    double radius,
    double red,
    double green,
    double blue,
    double alpha,
    float lineWidth
) {
    gl.lineWidth(lineWidth);
    gl.color(red, green, blue, alpha);
    gl.begin(3);
    for (int segment = 0; segment <= RAINBOW_SEGMENTS; segment++) {
        gl.vertex3(
            rainbowArcCos[segment] * radius,
            rainbowArcSin[segment] * radius,
            0
        );
    }
    gl.end();
}

void drawRainbowSparkles(long now, double arcProgress, double alpha) {
    double sparkleAlpha = alpha * 0.7;
    double sparkleSize = 0.15;
    double rotation = now * 0.003;
    double cosRotation = Math.cos(rotation);
    double sinRotation = Math.sin(rotation);

    gl.color(1.0, 1.0, 0.8, sparkleAlpha);

    for (int end = 0; end < 2; end++) {
        double endAngle = end == 0 ? 0.0 : Math.PI * arcProgress;
        double endX = Math.cos(endAngle) * RAINBOW_SIZE;
        double endY = Math.sin(endAngle) * RAINBOW_SIZE;

        drawSparkleRay(endX, endY, cosRotation, sinRotation, sparkleSize);
        drawSparkleRay(endX, endY, -sinRotation, cosRotation, sparkleSize);
        drawSparkleRay(endX, endY, -cosRotation, -sinRotation, sparkleSize);
        drawSparkleRay(endX, endY, sinRotation, -cosRotation, sparkleSize);
    }
}

void drawSparkleRay(double x, double y, double directionX, double directionY, double size) {
    gl.begin(3);
    gl.vertex3(x, y, 0);
    gl.vertex3(x + directionX * size, y + directionY * size, 0);
    gl.end();
}

void renderBedParticles(Vec3 camera, long now, long lifetime, float lineWidth) {
    gl.lineWidth(lineWidth);

    for (int i = 0; i < MAX_BED_PARTICLES; i++) {
        if (!bedParticleActive[i]) continue;

        long age = now - bedParticleTime[i];
        if (age > lifetime) {
            bedParticleActive[i] = false;
            activeBedParticleCount--;
            continue;
        }

        double progress = (double) age / (double) lifetime;
        double ageSeconds = age / 1000.0;

        double particleX = bedParticleX[i] + bedParticleVX[i] * ageSeconds;
        double particleY = bedParticleY[i] + bedParticleVY[i] * ageSeconds
            - 1.5 * ageSeconds * ageSeconds;
        double particleZ = bedParticleZ[i] + bedParticleVZ[i] * ageSeconds;

        particleX += Math.sin(age * 0.002 + i * 1.7) * 0.05;
        particleZ += Math.cos(age * 0.0015 + i * 2.3) * 0.05;

        double alpha;
        if (progress < 0.1) alpha = progress / 0.1;
        else if (progress > 0.7) alpha = (1.0 - progress) / 0.3;
        else alpha = 1.0;

        double scaleAnimation;
        if (progress < 0.1) scaleAnimation = progress / 0.1;
        else if (progress > 0.7) scaleAnimation = (1.0 - progress) / 0.3;
        else scaleAnimation = 1.0;

        double drawX = particleX - camera.xCoord;
        double drawY = particleY - camera.yCoord;
        double drawZ = particleZ - camera.zCoord;

        int colorIndex = i % 7;
        double red = bedParticleRed[colorIndex];
        double green = bedParticleGreen[colorIndex];
        double blue = bedParticleBlue[colorIndex];

        double size = bedParticleScale[i] * scaleAnimation;
        double billboardYaw = Math.toDegrees(Math.atan2(drawX, drawZ));

        gl.push();
        try {
            gl.translate(drawX, drawY, drawZ);
            gl.rotate(billboardYaw, 0, 1, 0);
            gl.rotate(ageSeconds * 40.0 + i * 60.0, 0, 0, 1);

            int type = bedParticleType[i];
            if (type == 0) {
                drawBedHeart(size, alpha, red, green, blue);
            } else if (type == 1) {
                drawBedStar(size, alpha, red, green, blue);
            } else if (type == 2) {
                drawBedDot(size, alpha, red, green, blue);
            } else {
                drawBedDiamond(size, alpha, red, green, blue);
            }
        } finally {
            gl.pop();
        }
    }
}

void drawBedHeart(double size, double baseAlpha, double red, double green, double blue) {
    double scale = size / 16.0;
    for (int layer = 2; layer >= 0; layer--) {
        double glowScale = scale * (1.0 + layer * 0.1);
        double layerAlpha = layer == 0
            ? baseAlpha * 0.9
            : baseAlpha * (0.25 / layer);

        gl.color(red, green, blue, layerAlpha);
        gl.begin(3);
        for (int i = 0; i <= BED_HEART_SEGMENTS; i++) {
            gl.vertex3(
                bedHeartShapeX[i] * glowScale,
                bedHeartShapeY[i] * glowScale,
                0
            );
        }
        gl.end();
    }
}

void drawBedStar(double size, double baseAlpha, double red, double green, double blue) {
    double scale = size / 16.0;
    for (int layer = 2; layer >= 0; layer--) {
        double glowScale = scale * (1.0 + layer * 0.1);
        double layerAlpha = layer == 0
            ? baseAlpha * 0.9
            : baseAlpha * (0.25 / layer);

        gl.color(red, green, blue, layerAlpha);
        gl.begin(3);
        for (int i = 0; i <= 8; i++) {
            gl.vertex3(starShapeX[i] * glowScale, starShapeY[i] * glowScale, 0);
        }
        gl.end();
    }
}

void drawBedDot(double size, double baseAlpha, double red, double green, double blue) {
    double scale = size / 2.0;
    for (int layer = 2; layer >= 0; layer--) {
        double glowScale = scale * (1.0 + layer * 0.15);
        double layerAlpha = layer == 0
            ? baseAlpha * 0.9
            : baseAlpha * (0.25 / layer);

        gl.color(red, green, blue, layerAlpha);
        gl.begin(6);
        gl.vertex3(0, 0, 0);
        for (int i = 0; i <= DOT_SEGMENTS; i++) {
            gl.vertex3(dotCircleX[i] * glowScale, dotCircleY[i] * glowScale, 0);
        }
        gl.end();
    }
}

void drawBedDiamond(double size, double baseAlpha, double red, double green, double blue) {
    double scale = size / 16.0;
    for (int layer = 2; layer >= 0; layer--) {
        double glowScale = scale * (1.0 + layer * 0.1);
        double layerAlpha = layer == 0
            ? baseAlpha * 0.9
            : baseAlpha * (0.25 / layer);

        gl.color(red, green, blue, layerAlpha);
        gl.begin(3);
        gl.vertex3(0, 14.0 * glowScale, 0);
        gl.vertex3(8.0 * glowScale, 0, 0);
        gl.vertex3(0, -14.0 * glowScale, 0);
        gl.vertex3(-8.0 * glowScale, 0, 0);
        gl.vertex3(0, 14.0 * glowScale, 0);
        gl.end();
    }
}

    private static final class NativeGl {
        void pushState() { GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS); }
        void popState() { GL11.glPopAttrib(); }
        void push() { GL11.glPushMatrix(); }
        void pop() { GL11.glPopMatrix(); }
        void begin(int mode) { GL11.glBegin(mode); }
        void end() { MiauDrawCalls.glEnd(); }
        void vertex3(double x, double y, double z) { GL11.glVertex3d(x, y, z); }
        void translate(double x, double y, double z) { GL11.glTranslated(x, y, z); }
        void rotate(double angle, double x, double y, double z) { GL11.glRotated(angle, x, y, z); }
        void color(double r, double g, double b, double a) { GL11.glColor4d(r, g, b, a); }
        void lineWidth(float width) { GL11.glLineWidth(width); }
        void depthMask(boolean enabled) { GL11.glDepthMask(enabled); }
        void blend(boolean enabled) {
            state(GL11.GL_BLEND, enabled);
            if (enabled) GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        void texture2d(boolean enabled) { state(GL11.GL_TEXTURE_2D, enabled); }
        void cull(boolean enabled) { state(GL11.GL_CULL_FACE, enabled); }
        void depth(boolean enabled) { state(GL11.GL_DEPTH_TEST, enabled); }
        void lineSmooth(boolean enabled) { state(GL11.GL_LINE_SMOOTH, enabled); }
        void resetColor() { GL11.glColor4f(1F, 1F, 1F, 1F); }
        private void state(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability);
            else GL11.glDisable(capability);
        }
    }

}
