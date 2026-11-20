package idle.miau.module.impl.render;

import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.world.WorldEvent;
import idle.miau.module.Module;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.Utils;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class Fireflies extends Module {
    private static final int MAX_FIREFLIES = 48;
    private static final int DEFAULT_COLOR = 0xFFFFD46A;
    private static final String[] MODES = {"Fireflies", "Cinematic"};
    private static final String[] COLOR_MODES = {"Default", "HUD"};

    private final ButtonSetting darkImprint;
    private final ButtonSetting lighting;
    private final SliderSetting spawnDelay;
    private final SliderSetting colorMode;
    private final SliderSetting particleSize;
    private final SliderSetting density;
    private final SliderSetting flightSpeed;
    private final SliderSetting trailLength;
    private final SliderSetting mode;
    private final List<FirePart> partList = new ArrayList<>();
    private final Random random = new Random();
    private ResourceLocation glowTexture;
    private int spawnTicks;
    private int activeMode;

    public Fireflies() {
        super("Fireflies", category.render);
        setDescription("Surrounds you with fireflies or cinematic glowing particles, trails and sparks.");
        registerSetting(darkImprint = new ButtonSetting("Dark imprint", false));
        registerSetting(lighting = new ButtonSetting("Lighting", true));
        registerSetting(spawnDelay = new SliderSetting("Spawn delay", 3.0, 1.0, 10.0, 1.0));
        registerSetting(colorMode = new SliderSetting("Color mode", 0, COLOR_MODES));
        registerSetting(particleSize = new SliderSetting("Particle size", 1.0, 0.5, 2.0, 0.1));
        registerSetting(density = new SliderSetting("Density", 28, 4, MAX_FIREFLIES, 1));
        registerSetting(flightSpeed = new SliderSetting("Flight speed", "x", 1.0, 0.25, 2.0, 0.05));
        registerSetting(trailLength = new SliderSetting("Trail length", "ms", 450, 0, 1000, 50));
        registerSetting(mode = new SliderSetting("Mode", 0, MODES));
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    private boolean isCinematic() {
        return (int) mode.getInput() == 1;
    }

    int getPartColor(double phase) {
        return (int) colorMode.getInput() == 1
                ? ArrayListModule.getHudColorAtPosition(phase * 11.0, phase * 17.0)
                : DEFAULT_COLOR;
    }

    @Override
    public void onEnable() {
        clearParticles();
        if (glowTexture == null) {
            DynamicTexture texture = new DynamicTexture(createParticleTexture());
            texture.setBlurMipmap(true, false);
            glowTexture = mc.getTextureManager().getDynamicTextureLocation("mimeow/fireflies-glow", texture);
        }
    }

    @Override
    public void onDisable() {
        clearParticles();
        if (glowTexture != null) {
            mc.getTextureManager().deleteTexture(glowTexture);
            glowTexture = null;
        }
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        clearParticles();
    }

    private void clearParticles() {
        partList.clear();
        spawnTicks = 0;
        activeMode = (int) mode.getInput();
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            clearParticles();
            return;
        }
        if (mc.thePlayer.ticksExisted <= 1 || activeMode != (int) mode.getInput()) {
            clearParticles();
        }
        for (FirePart part : partList) {
            part.update();
        }
        Vec3 player = mc.thePlayer.getPositionVector();
        partList.removeIf(part -> part.age >= part.lifetime || part.position.squareDistanceTo(player) > 1024.0);
        int limit = Math.min(MAX_FIREFLIES, (int) density.getInput());
        while (partList.size() > limit) {
            partList.remove(0);
        }
        if (++spawnTicks >= (int) spawnDelay.getInput()) {
            spawnTicks = 0;
            int count = isCinematic() ? 2 : 1;
            for (int index = 0; index < count && partList.size() < limit; index++) {
                Vec3 position = findSpawnPosition();
                if (position != null) {
                    partList.add(new FirePart(position, isCinematic()));
                }
            }
        }
    }

    private Vec3 findSpawnPosition() {
        Vec3 player = mc.thePlayer.getPositionVector();
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double radius = isCinematic()
                    ? 2.0 + random.nextDouble() * 12.0 : 2.0 + Math.sqrt(random.nextDouble()) * 8.0;
            Vec3 position = player.addVector(Math.cos(angle) * radius,
                    isCinematic() ? -1.0 + random.nextDouble() * 6.0 : 0.5 + random.nextDouble() * 3.5,
                    Math.sin(angle) * radius);
            if (isFree(position)) {
                return position;
            }
        }
        return null;
    }

    private boolean isFree(Vec3 position) {
        return mc.theWorld.getCollisionBoxes(new AxisAlignedBB(
                position.xCoord - 0.06, position.yCoord - 0.06, position.zCoord - 0.06,
                position.xCoord + 0.06, position.yCoord + 0.06, position.zCoord + 0.06)).isEmpty();
    }

    @Subscribe
    public void onRender3D(RenderWorldLastEvent event) {
        if (!Utils.nullCheck() || partList.isEmpty() || glowTexture == null) {
            return;
        }
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        boolean lightmapEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        mc.entityRenderer.disableLightmap();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(glowTexture);
            Vec3 camera = new Vec3(mc.getRenderManager().viewerPosX,
                    mc.getRenderManager().viewerPosY, mc.getRenderManager().viewerPosZ);
            double yaw = Math.toRadians(mc.getRenderManager().playerViewY);
            double pitch = Math.toRadians(mc.getRenderManager().playerViewX)
                    * (mc.gameSettings.thirdPersonView == 2 ? -1.0 : 1.0);
            Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw));
            Vec3 up = new Vec3(Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch),
                    -Math.cos(yaw) * Math.sin(pitch));
            renderParticles(event.partialTicks, camera, right, up);
        } finally {
            GL11.glPopAttrib();
            GlStateManager.bindTexture(previousTexture);
            if (lightmapEnabled) {
                mc.entityRenderer.enableLightmap();
            }
            GlStateManager.setActiveTexture(previousActiveTexture);
        }
    }

    private void renderParticles(float partialTicks, Vec3 camera, Vec3 right, Vec3 up) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer buffer = tessellator.getWorldRenderer();
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA,
                darkImprint.isToggled() ? GL11.GL_ONE_MINUS_SRC_ALPHA : GL11.GL_ONE,
                GL11.GL_ONE, GL11.GL_ZERO);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (FirePart part : partList) {
            float alpha = part.alpha(partialTicks, camera);
            int color = getPartColor(part.phase);
            if (part.cinematic) {
                appendTrail(buffer, part, camera, partialTicks, color, alpha, 0.42, 0.16F);
                appendTrail(buffer, part, camera, partialTicks, brighten(color), alpha, 0.075, 0.65F);
            } else {
                appendTrail(buffer, part, camera, partialTicks, color, alpha, 0.085, 0.35F);
            }
        }
        tessellator.draw();
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (FirePart part : partList) {
            Vec3 position = interpolate(part.previous, part.position, partialTicks).subtract(camera);
            float alpha = part.alpha(partialTicks, camera);
            int color = getPartColor(part.phase);
            double size = particleSize.getInput() * part.size;
            if (part.cinematic) {
                appendCinematicParticle(buffer, part, position, right, up, size, color, alpha, partialTicks);
            } else {
                if (lighting.isToggled()) {
                    appendSprite(buffer, position, right, up, size * 3.2, size * 3.2, color, alpha * 0.12F);
                }
                appendSprite(buffer, position, right, up, size, size, color, alpha * 0.7F);
                appendSprite(buffer, position, right, up, size * 0.27, size * 0.36,
                        brighten(color), alpha);
            }
            for (Spark spark : part.sparks) {
                float renderAge = spark.age + partialTicks;
                float fade = 1.0F - renderAge / spark.lifetime;
                if (fade > 0.0F) {
                    Vec3 sparkPosition = interpolate(spark.previous, spark.position, partialTicks).subtract(camera);
                    float sparkAlpha = alpha * fade * fade * 0.45F;
                    if (part.cinematic) {
                        sparkAlpha *= smoothStep(renderAge / 3.0F);
                        appendSprite(buffer, sparkPosition, right, up, size * 0.5, size * 0.5,
                                color, sparkAlpha * 0.12F);
                    }
                    double sparkSize = size * spark.size;
                    appendSprite(buffer, sparkPosition, right, up, sparkSize, sparkSize,
                            part.cinematic ? brighten(color) : color, sparkAlpha);
                }
            }
        }
        tessellator.draw();
    }

    private void appendCinematicParticle(WorldRenderer buffer, FirePart part, Vec3 position,
                                         Vec3 right, Vec3 up, double size, int color, float alpha,
                                         float partialTicks) {
        float distance = (float) position.lengthVector();
        float defocus = smoothStep(Math.abs(distance - 7.0F) / 8.0F);
        double breathing = 1.0 + Math.sin((part.age + partialTicks) * 0.035 + part.phase) * 0.08;
        size *= breathing;
        if (lighting.isToggled()) {
            double haloSize = size * (4.5 + defocus * 2.0);
            appendSprite(buffer, position, right, up, haloSize, haloSize, color, alpha * 0.07F);
            appendSprite(buffer, position, right, up, size * 2.2, size * 2.2, color, alpha * 0.16F);
            float flare = alpha * (1.0F - defocus) * 0.11F;
            appendSprite(buffer, position, right, up, size * 7.0, size * 0.18, color, flare);
            appendSprite(buffer, position, right, up, size * 0.14, size * 2.0, color, flare * 0.3F);
            if (defocus > 0.01F) {
                double bokehSize = size * (1.8 + defocus * 2.8);
                appendSprite(buffer, position, right, up, bokehSize, bokehSize,
                        color, alpha * defocus * 0.22F, 0.5, 1.0);
            }
        }
        appendSprite(buffer, position, right, up, size * (1.0 + defocus), size * (1.0 + defocus),
                color, alpha * (0.75F - defocus * 0.25F));
        double coreSize = size * (0.2 + defocus * 0.12);
        appendSprite(buffer, position, right, up, coreSize, coreSize, brighten(color), alpha * 0.9F);
    }

    private void appendTrail(WorldRenderer buffer, FirePart part, Vec3 camera,
                             float partialTicks, int color, float alpha, double width, float opacity) {
        if (part.trail.size() < 2 || trailLength.getInput() <= 0.0) {
            return;
        }
        TrailPoint previous = null;
        double duration = trailLength.getInput() / 50.0 * (part.cinematic ? 1.8 : 1.0);
        double size = particleSize.getInput() * part.size * width;
        for (TrailPoint point : part.trail) {
            if (previous != null) {
                Vec3 from = previous.position.subtract(camera);
                Vec3 to = (point.age == part.age
                        ? interpolate(part.previous, part.position, partialTicks) : point.position).subtract(camera);
                Vec3 side = to.subtract(from).crossProduct(from).normalize();
                float fromFade = MathHelper.clamp_float((float) (1.0 - (part.age + partialTicks - previous.age) / duration), 0.0F, 1.0F);
                float toFade = MathHelper.clamp_float((float) (1.0 - (part.age + partialTicks - point.age) / duration), 0.0F, 1.0F);
                vertex(buffer, from.addVector(side.xCoord * size * fromFade, side.yCoord * size * fromFade,
                        side.zCoord * size * fromFade), 0.25, 0.0, color, alpha * fromFade * fromFade * opacity);
                vertex(buffer, from.addVector(-side.xCoord * size * fromFade, -side.yCoord * size * fromFade,
                        -side.zCoord * size * fromFade), 0.25, 1.0, color, alpha * fromFade * fromFade * opacity);
                vertex(buffer, to.addVector(-side.xCoord * size * toFade, -side.yCoord * size * toFade,
                        -side.zCoord * size * toFade), 0.25, 1.0, color, alpha * toFade * toFade * opacity);
                vertex(buffer, to.addVector(side.xCoord * size * toFade, side.yCoord * size * toFade,
                        side.zCoord * size * toFade), 0.25, 0.0, color, alpha * toFade * toFade * opacity);
            }
            previous = point;
        }
    }

    private static void appendSprite(WorldRenderer buffer, Vec3 position, Vec3 right, Vec3 up,
                                     double width, double height, int color, float alpha) {
        appendSprite(buffer, position, right, up, width, height, color, alpha, 0.0, 0.5);
    }

    private static void appendSprite(WorldRenderer buffer, Vec3 position, Vec3 right, Vec3 up,
                                     double width, double height, int color, float alpha,
                                     double minU, double maxU) {
        vertex(buffer, corner(position, right, up, -width, -height), minU, 1.0, color, alpha);
        vertex(buffer, corner(position, right, up, -width, height), minU, 0.0, color, alpha);
        vertex(buffer, corner(position, right, up, width, height), maxU, 0.0, color, alpha);
        vertex(buffer, corner(position, right, up, width, -height), maxU, 1.0, color, alpha);
    }

    private static Vec3 corner(Vec3 position, Vec3 right, Vec3 up, double x, double y) {
        return position.addVector(right.xCoord * x + up.xCoord * y,
                right.yCoord * x + up.yCoord * y, right.zCoord * x + up.zCoord * y);
    }

    private static void vertex(WorldRenderer buffer, Vec3 position, double u, double v, int color, float alpha) {
        int opacity = (int) (MathHelper.clamp_float(alpha, 0.0F, 1.0F) * (color >>> 24));
        buffer.pos(position.xCoord, position.yCoord, position.zCoord).tex(u, v)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, opacity).endVertex();
    }

    private static int brighten(int color) {
        int red = Math.round((color >> 16 & 255) * 0.82F + 255.0F * 0.18F);
        int green = Math.round((color >> 8 & 255) * 0.82F + 255.0F * 0.18F);
        int blue = Math.round((color & 255) * 0.82F + 255.0F * 0.18F);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    static Vec3 interpolate(Vec3 previous, Vec3 current, float partialTicks) {
        return previous.addVector((current.xCoord - previous.xCoord) * partialTicks,
                (current.yCoord - previous.yCoord) * partialTicks,
                (current.zCoord - previous.zCoord) * partialTicks);
    }

    static float lifetimeAlpha(float age, int lifetime) {
        return smoothStep(age / 12.0F) * smoothStep((lifetime - age) / 30.0F);
    }

    private static float smoothStep(float value) {
        float clamped = MathHelper.clamp_float(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    static BufferedImage createGlowTexture() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                double dx = (x + 0.5) / 32.0 - 1.0;
                double dy = (y + 0.5) / 32.0 - 1.0;
                double radius = dx * dx + dy * dy;
                double glow = (0.8 * Math.exp(-8.0 * radius) + 0.2 * Math.exp(-40.0 * radius))
                        * Math.max(0.0, 1.0 - radius);
                int alpha = (int) Math.round(glow * 255.0);
                image.setRGB(x, y, alpha << 24 | 0xFFFFFF);
            }
        }
        return image;
    }

    static BufferedImage createParticleTexture() {
        BufferedImage glow = createGlowTexture();
        BufferedImage atlas = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                atlas.setRGB(x, y, glow.getRGB(x, y));
                double dx = (x + 0.5) / 32.0 - 1.0;
                double dy = (y + 0.5) / 32.0 - 1.0;
                double radius = Math.sqrt(dx * dx + dy * dy);
                double ring = Math.exp(-Math.pow((radius - 0.62) / 0.14, 2.0));
                double bokeh = (0.18 * Math.exp(-radius * radius * 2.5) + 0.42 * ring)
                        * smoothStep((float) ((1.0 - radius) / 0.22));
                atlas.setRGB(x + 64, y, (int) Math.round(bokeh * 255.0) << 24 | 0xFFFFFF);
            }
        }
        return atlas;
    }

    private final class FirePart {
        private final Deque<TrailPoint> trail = new ArrayDeque<>();
        private final List<Spark> sparks = new ArrayList<>();
        private final double phase = random.nextDouble() * Math.PI * 2.0;
        private final boolean cinematic;
        private final double size;
        private final int lifetime;
        private final double speed;
        private final double verticalMotion;
        private Vec3 position;
        private Vec3 previous;
        private Vec3 velocity = new Vec3(0.0, 0.0, 0.0);
        private double heading = random.nextDouble() * Math.PI * 2.0;
        private double targetHeading = heading;
        private int nextTurn;
        private int age;

        private FirePart(Vec3 position, boolean cinematic) {
            this.position = position;
            this.previous = position;
            this.cinematic = cinematic;
            this.size = cinematic ? 0.2 + random.nextDouble() * 0.1 : 0.13 + random.nextDouble() * 0.07;
            this.lifetime = cinematic ? 140 + random.nextInt(61) : 160 + random.nextInt(81);
            this.speed = cinematic ? 0.09 + random.nextDouble() * 0.09 : 0.035 + random.nextDouble() * 0.035;
            this.verticalMotion = (random.nextDouble() - 0.4) * 0.09;
            this.nextTurn = 12 + random.nextInt(19);
        }

        private float alpha(float partialTicks, Vec3 camera) {
            float renderAge = age + partialTicks;
            double time = renderAge / 20.0;
            double pulse = 0.5 + 0.5 * Math.sin(time * (cinematic ? 0.9 : 2.3) + phase);
            float distance = (float) Math.sqrt(position.squareDistanceTo(camera));
            float fade = cinematic
                    ? smoothStep(renderAge / 24.0F) * smoothStep((lifetime - renderAge) / 36.0F)
                    : lifetimeAlpha(renderAge, lifetime);
            return fade * (float) (cinematic ? 0.65 + 0.35 * pulse : 0.35 + 0.65 * pulse * pulse)
                    * smoothStep((distance - 0.35F) / 1.1F) * smoothStep((32.0F - distance) / 6.0F);
        }

        private void update() {
            previous = position;
            age++;
            double time = age / 20.0;
            double motion = speed * flightSpeed.getInput();
            double vertical;
            if (cinematic) {
                if (age >= nextTurn) {
                    targetHeading = heading + (random.nextDouble() - 0.5) * Math.PI * 1.6;
                    nextTurn = age + 12 + random.nextInt(19);
                }
                double turn = Math.atan2(Math.sin(targetHeading - heading), Math.cos(targetHeading - heading));
                heading += turn * 0.065;
                motion *= Math.exp(-age * 0.004);
                vertical = (verticalMotion * Math.exp(-age * 0.018)
                        + Math.sin(time * 0.8 + phase) * 0.012) * flightSpeed.getInput();
            } else {
                heading += Math.sin(time * 0.8 + phase) * 0.035;
                vertical = Math.sin(time * 1.4 + phase) * motion * 0.35;
            }
            Vec3 desired = new Vec3(Math.cos(heading) * motion, vertical, Math.sin(heading) * motion);
            velocity = interpolate(velocity, desired, cinematic ? 0.18F : 0.12F);
            Vec3 next = position.addVector(velocity.xCoord, velocity.yCoord, velocity.zCoord);
            if (isFree(next)) {
                position = next;
            } else {
                heading += Math.PI * 0.65;
                targetHeading = heading;
                velocity = new Vec3(-velocity.xCoord * 0.35, -velocity.yCoord * 0.35, -velocity.zCoord * 0.35);
            }
            trail.addLast(new TrailPoint(position, age));
            int trailTicks = (int) Math.ceil(trailLength.getInput() / 50.0 * (cinematic ? 1.8 : 1.0));
            while (!trail.isEmpty() && age - trail.peekFirst().age > trailTicks) {
                trail.removeFirst();
            }
            for (Spark spark : sparks) {
                spark.previous = spark.position;
                spark.position = spark.position.addVector(spark.motion.xCoord, spark.motion.yCoord, spark.motion.zCoord);
                if (cinematic) {
                    spark.motion = new Vec3(spark.motion.xCoord * 0.97,
                            spark.motion.yCoord * 0.97 - 0.0005, spark.motion.zCoord * 0.97);
                }
                spark.age++;
            }
            sparks.removeIf(spark -> spark.age >= spark.lifetime);
            if (cinematic && age % 2 == 0) {
                for (int index = 0; index < 2; index++) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    double scatter = 0.012 + random.nextDouble() * 0.025;
                    sparks.add(new Spark(position, new Vec3(Math.cos(angle) * scatter + velocity.xCoord * 0.15,
                            (random.nextDouble() - 0.5) * 0.035, Math.sin(angle) * scatter + velocity.zCoord * 0.15),
                            22 + random.nextInt(9), 0.07 + random.nextDouble() * 0.08));
                }
            } else if (!cinematic && age % 8 == 0) {
                sparks.add(new Spark(position, new Vec3((random.nextDouble() - 0.5) * 0.012,
                        -0.008 - random.nextDouble() * 0.008, (random.nextDouble() - 0.5) * 0.012), 16, 0.12));
            }
        }
    }

    private static final class Spark {
        private Vec3 position;
        private Vec3 previous;
        private Vec3 motion;
        private final int lifetime;
        private final double size;
        private int age;

        private Spark(Vec3 position, Vec3 motion, int lifetime, double size) {
            this.position = position;
            this.previous = position;
            this.motion = motion;
            this.lifetime = lifetime;
            this.size = size;
        }
    }

    private static final class TrailPoint {
        private final Vec3 position;
        private final int age;

        private TrailPoint(Vec3 position, int age) {
            this.position = position;
            this.age = age;
        }
    }
}
