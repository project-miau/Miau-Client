package idle.miau.module.impl.render;

import idle.miau.performance.MiauDrawCalls;
import idle.miau.Miau;
import idle.miau.mcp.accessor.IAccessorEntityRenderer;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.TeamUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.shader.GlowShader;
import idle.miau.utility.shader.OutlineShader;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.RenderHelper;
import idle.miau.mcp.event.client.RenderPlayerEvent;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;
import idle.miau.mcp.event.game.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerESP extends Module {
    private static final String[] MODES = {"Miau", "Skeleton", "Raven"};
    private static final String[] MIAU_STYLES = {"None", "2D", "3D", "Outline", "FakeCorner", "Fake2D"};
    private static final String[] MIAU_COLORS = {"Default", "Teams", "HUD"};
    private static final String[] MIAU_HEALTH_BARS = {"None", "2D", "Raven"};
    private static final int RAVEN_MODE = 2;
    private static final int SKELETON_MODE = 1;
    private static final int MIAU_MODE = 0;

    public SliderSetting mode;
    public SliderSetting thickness;
    public ColorSetting color;

    public ButtonSetting teamColor;
    public ButtonSetting rainbow;

    public GroupSetting espTypes;
    public ButtonSetting twoD;
    public ButtonSetting box;
    public ButtonSetting healthBar;
    public ButtonSetting outline;
    public ButtonSetting shaded;
    public ButtonSetting ring;

    public SliderSetting boxStyle;
    public ButtonSetting names;
    public ButtonSetting armor;
    public ButtonSetting items;
    public ButtonSetting itemIcons;

    public ButtonSetting redOnDamage;
    public GroupSetting targets;
    public ButtonSetting renderSelf;
    public ButtonSetting showInvis;
    public ButtonSetting showTeammate;

    private final SliderSetting maxDistance;
    private final SliderSetting miauStyle;
    private final SliderSetting miauColor;
    private final SliderSetting miauHealthBar;
    private final ButtonSetting miauPlayers;
    private final ButtonSetting miauFriends;
    private final ButtonSetting miauEnemies;
    private final ButtonSetting miauSelf;
    private final ButtonSetting miauBots;

    private static final float RAD_TO_DEG = 57.29578F;
    public static boolean renderingOutlinePass = false;

    private final List<EspRenderState> renderStates = new ArrayList<>();
    private final List<EspRenderState> visibleRenderStates = new ArrayList<>();
    private final Map<EntityPlayer, EspRenderState> playerRenderStates = new HashMap<>();
    private final double[] projectedPoint = new double[3];
    private RenderUtils.ProjectionContext projectionContext;
    private int renderStateCount;
    private int visibleRenderStateCount;

    private Framebuffer outlineFramebuffer;
    private final OutlineShader outlineShader = new OutlineShader();
    private final GlowShader glowShader = new GlowShader();

    private static final class EspRenderState {
        private EntityLivingBase entity;
        private int staticColor;
        private int renderColor;

        private void set(EntityLivingBase entity, int staticColor) {
            this.entity = entity;
            this.staticColor = staticColor;
            this.renderColor = staticColor;
        }
    }

    public PlayerESP() {
        super("PlayerESP", category.render, 0);
        setDescription("Highlights players through walls.");
        this.registerSetting(mode = new SliderSetting("Mode", MIAU_MODE, MODES));
        this.registerSetting(thickness = new SliderSetting(
                "Thickness", "x", 1.0D, 0.25D, 2.0D, 0.05D));
        this.registerSetting(espTypes = new GroupSetting("Types"));
        this.registerSetting(twoD = new ButtonSetting(espTypes, "2D", false));
        this.registerSetting(box = new ButtonSetting(espTypes, "Box", false));
        this.registerSetting(outline = new ButtonSetting(espTypes, "Outline", false));
        this.registerSetting(ring = new ButtonSetting(espTypes, "Ring", false));
        this.registerSetting(shaded = new ButtonSetting(espTypes, "Shaded", false));
        this.registerSetting(boxStyle = new SliderSetting("Box Style", 0, new String[]{"Box", "Corner A", "Corner B", "Split"}));
        this.registerSetting(names = new ButtonSetting("Names", false));
        this.registerSetting(armor = new ButtonSetting("Armor", false));
        this.registerSetting(items = new ButtonSetting("Items", false));
        this.registerSetting(itemIcons = new ButtonSetting("Item Icons", false));
        this.registerSetting(color = new ColorSetting("Color", 0, 255, 0));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", false));
        this.registerSetting(healthBar = new ButtonSetting("Health bar", true));
        this.registerSetting(redOnDamage = new ButtonSetting("Red on damage", true));
        this.registerSetting(teamColor = new ButtonSetting("Team color", false));
        this.registerSetting(targets = new GroupSetting("Targets"));
        this.registerSetting(renderSelf = new ButtonSetting(targets, "Render self", false));
        this.registerSetting(showInvis = new ButtonSetting(targets, "Show invis", true));
        this.registerSetting(showTeammate = new ButtonSetting(targets, "Show Teammate", true));
        this.registerSetting(maxDistance = new SliderSetting("Max distance", 128.0, 32.0, 256.0, 8.0));
        this.registerSetting(miauStyle = new SliderSetting("ESP style", 2, MIAU_STYLES));
        this.registerSetting(miauColor = new SliderSetting("ESP color", 0, MIAU_COLORS));
        this.registerSetting(miauHealthBar = new SliderSetting("ESP health bar", 0, MIAU_HEALTH_BARS));
        this.registerSetting(miauPlayers = new ButtonSetting("Players", true));
        this.registerSetting(miauFriends = new ButtonSetting("Friends", true));
        this.registerSetting(miauEnemies = new ButtonSetting("Enemies", true));
        this.registerSetting(miauSelf = new ButtonSetting("Self", false));
        this.registerSetting(miauBots = new ButtonSetting("Bots", false));
        guiUpdate();
    }

    @Override
    public String getInfo() {
        return MODES[(int) mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean ravenMode = isRavenMode();
        boolean miauMode = isMiauMode();
        targets.setVisible(!miauMode, this);
        thickness.setVisible(isSkeletonMode(), this);
        espTypes.setVisible(ravenMode, this);
        twoD.setVisible(ravenMode, this);
        box.setVisible(ravenMode, this);
        outline.setVisible(ravenMode, this);
        ring.setVisible(ravenMode, this);
        shaded.setVisible(ravenMode, this);
        healthBar.setVisible(ravenMode, this);
        boolean isExhibition2D = ravenMode && twoD.isToggled();
        boxStyle.setVisible(isExhibition2D, this);
        names.setVisible(isExhibition2D, this);
        armor.setVisible(isExhibition2D, this);
        items.setVisible(isExhibition2D, this);
        itemIcons.setVisible(isExhibition2D, this);
        miauStyle.setVisible(miauMode, this);
        miauColor.setVisible(miauMode, this);
        miauHealthBar.setVisible(miauMode, this);
        miauPlayers.setVisible(miauMode, this);
        miauFriends.setVisible(miauMode, this);
        miauEnemies.setVisible(miauMode, this);
        miauSelf.setVisible(miauMode, this);
        miauBots.setVisible(miauMode, this);
        renderSelf.setVisible(!miauMode, this);
        color.setVisible(!miauMode, this);
        rainbow.setVisible(!miauMode, this);
        teamColor.setVisible(!miauMode, this);
        redOnDamage.setVisible(!miauMode, this);
        showInvis.setVisible(!miauMode, this);
        showTeammate.setVisible(!miauMode, this);
        maxDistance.setVisible(!miauMode, this);
    }

    @Override
    public void onSlide(SliderSetting setting) {
        if (setting == mode) {
            guiUpdate();
        }
    }

    @Override
    public void onCommandSettingChanged(idle.miau.module.setting.Setting setting) {
        if (setting == mode) {
            guiUpdate();
        }
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == twoD) {
            guiUpdate();
        }
        super.guiButtonToggled(setting);
    }

    public boolean isRavenMode() {
        return (int) mode.getInput() == RAVEN_MODE;
    }

    boolean isMiauMode() {
        return (int) mode.getInput() == MIAU_MODE;
    }

    boolean isSkeletonMode() {
        return (int) mode.getInput() == SKELETON_MODE;
    }

    @Override
    public void onDisable() {
        renderingOutlinePass = false;
        clearRenderStates();
        if (outlineFramebuffer != null) {
            outlineFramebuffer.deleteFramebuffer();
            outlineFramebuffer = null;
        }
    }

    @Subscribe
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!Utils.nullCheck() || mc.theWorld == null) {
            clearRenderStates();
            return;
        }

        updateRenderStates();
    }

    @Subscribe
    public void onRenderPlayerEvent(RenderPlayerEvent.Post e) {
        if (!isSkeletonMode() || e.entityPlayer == null || !Utils.nullCheck()
                || renderingOutlinePass) {
            return;
        }

        EspRenderState renderState = playerRenderStates.get(e.entityPlayer);
        if (renderState == null) {
            return;
        }

        int renderColor = resolveBaseRenderColor(renderState);
        if (redOnDamage.isToggled() && e.entityPlayer.hurtTime != 0) {
            renderColor = 0xFFFF0000;
        }
        this.renderSkeleton(e.entityPlayer, e.renderer.getMainModel(), renderColor,
                e.partialRenderTick, e.x, e.y, e.z);
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        this.visibleRenderStateCount = 0;
        if (!Utils.nullCheck() || (!isRavenMode() && !isMiauMode())) {
            return;
        }

        boolean miauMode = isMiauMode();
        int style = (int) miauStyle.getInput();
        boolean renderWorldTypes = miauMode
                ? style == 2 || style == 4 || style == 5 || (int) miauHealthBar.getInput() == 2
                : box.isToggled() || shaded.isToggled() || healthBar.isToggled() || ring.isToggled();
        boolean captureVisibleStates = miauMode
                ? style == 1 || style == 3 || (int) miauHealthBar.getInput() == 1
                : outline.isToggled() || twoD.isToggled();
        if (renderStateCount == 0 || (!renderWorldTypes && !captureVisibleStates)) {
            return;
        }

        for (int i = 0; i < renderStateCount; i++) {
            EspRenderState renderState = renderStates.get(i);
            EntityLivingBase entity = renderState.entity;
            if (entity == null || !(miauMode
                    ? entity.ignoreFrustumCheck || RenderUtils.isInViewFrustum(
                            entity.getEntityBoundingBox().expand(0.1D, 0.1D, 0.1D))
                    : RenderUtils.isInViewFrustum(entity))) {
                continue;
            }

            renderState.renderColor = resolveBaseRenderColor(renderState);
            if (renderWorldTypes) {
                if (miauMode) {
                    renderMiauWorld((EntityPlayer) entity, renderState.renderColor, e.partialTicks);
                } else {
                    this.render(entity, renderState.renderColor);
                }
            }
            if (captureVisibleStates) {
                addVisibleRenderState(renderState);
            }
        }
    }

    @Subscribe(priority = Priority.LOWEST)
    public void onRenderTwo2D(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || (!isRavenMode() && !isMiauMode()) || visibleRenderStateCount == 0) {
            return;
        }
        if (isMiauMode()) {
            if ((int) miauStyle.getInput() == 3) runOutlinePass(e.partialTicks);
            if ((int) miauStyle.getInput() == 1 || (int) miauHealthBar.getInput() == 1) {
                renderTwoDPass(e.partialTicks);
            }
            return;
        }
        if (outline.isToggled()) runOutlinePass(e.partialTicks);
        if (twoD.isToggled()) {
            renderTwoDPass(e.partialTicks);
        }
    }

    private void runOutlinePass(float partialTicks) {
        if (!outlineShader.isValid() || !glowShader.isValid() || visibleRenderStateCount == 0) return;
        outlineFramebuffer = RenderUtils.createFrameBuffer(outlineFramebuffer, false);
        if (outlineFramebuffer == null) return;

        boolean shadows = mc.gameSettings.entityShadows;
        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            outlineFramebuffer.bindFramebuffer(false);
            ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
            mc.gameSettings.entityShadows = false;
            renderingOutlinePass = true;

            glowShader.use();
            for (int i = 0; i < visibleRenderStateCount; i++) {
                EspRenderState renderState = visibleRenderStates.get(i);
                EntityLivingBase ent = renderState.entity;
                int col = resolveOutlineColor(renderState);
                glowShader.setColor((col >> 16) & 0xFF, (col >> 8) & 0xFF, col & 0xFF, (col >> 24) & 0xFF);
                boolean invis = ent.isInvisible();
                try {
                    if (isMiauMode() || showInvis.isToggled()) ent.setInvisible(false);
                    mc.getRenderManager().renderEntityStatic(ent, partialTicks, true);
                }
                finally {
                    ent.setInvisible(invis);
                }
            }
            glowShader.stop();
            renderingOutlinePass = false;

            mc.entityRenderer.disableLightmap();
            mc.entityRenderer.setupOverlayRendering();
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            outlineShader.use();
            RenderUtils.drawFramebufferFullscreen(outlineFramebuffer);
            outlineShader.stop();
            outlineFramebuffer.framebufferClear();
        }
        finally {
            glowShader.stop();
            outlineShader.stop();
            renderingOutlinePass = false;
            mc.gameSettings.entityShadows = shadows;
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
        }
    }

    private void clearRenderStates() {
        renderStateCount = 0;
        visibleRenderStateCount = 0;
        playerRenderStates.clear();
        renderStates.clear();
        visibleRenderStates.clear();
    }

    private void updateRenderStates() {
        renderStateCount = 0;
        visibleRenderStateCount = 0;
        playerRenderStates.clear();

        double maxDistSq = isMiauMode() ? 512.0D * 512.0D
                : maxDistance.getInput() * maxDistance.getInput();
        if (Miau.DEBUG && !isMiauMode()) {
            for (Entity entity : mc.theWorld.loadedEntityList) {
                if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer) {
                    continue;
                }
                if (entity instanceof EntityPlayer
                        && !showTeammate.isToggled()
                        && Utils.isTeammate(entity)) {
                    continue;
                }
                if (!RenderUtils.isWithinDistanceSqToRenderView(entity, maxDistSq)) {
                    continue;
                }
                addRenderState((EntityLivingBase) entity, resolveStaticColor(entity));
            }
            return;
        }

        EntityPlayer selfPlayer = mc.thePlayer;
        boolean allowSelf = shouldRenderSelf(selfPlayer);
        List<EntityPlayer> players = mc.theWorld.playerEntities;
        if (isMiauMode()) {
            players = new ArrayList<>(players);
            players.sort(Comparator
                    .comparingDouble((EntityPlayer player) -> mc.getRenderManager()
                            .getDistanceToCamera(player.posX, player.posY, player.posZ))
                    .reversed()
                    .thenComparing(player -> player.getUniqueID().toString()));
        }
        for (EntityPlayer player : players) {
            if (player == selfPlayer && !allowSelf) {
                continue;
            }
            if (player.deathTime != 0) {
                continue;
            }
            if (isMiauMode()) {
                if (!isMiauTarget(player)) {
                    continue;
                }
            } else {
                if (!showInvis.isToggled() && player.isInvisible()) {
                    continue;
                }
                if (selfPlayer != player && !showTeammate.isToggled() && Utils.isTeammate(player)) {
                    continue;
                }
                if (selfPlayer != player && AntiBot.isBot(player)) {
                    continue;
                }
            }
            if (!RenderUtils.isWithinDistanceSqToRenderView(player, maxDistSq)) {
                continue;
            }
            addRenderState(player, resolveStaticColor(player));
        }
    }

    private boolean shouldRenderSelf(EntityPlayer selfPlayer) {
        return selfPlayer == mc.thePlayer
                && (isMiauMode() ? miauSelf : renderSelf).isToggled()
                && mc.gameSettings.thirdPersonView > 0;
    }

    private boolean isMiauTarget(EntityPlayer player) {
        if (player == mc.thePlayer || player == mc.getRenderViewEntity()) {
            return miauSelf.isToggled() && mc.gameSettings.thirdPersonView != 0;
        }
        if (TeamUtils.isBot(player)) {
            return miauBots.isToggled();
        }
        if (Utils.isFriended(player)) {
            return miauFriends.isToggled();
        }
        if (Utils.isEnemy(player)) {
            return miauEnemies.isToggled();
        }
        return miauPlayers.isToggled();
    }

    private void addRenderState(EntityLivingBase entity, int staticColor) {
        if (renderStateCount >= renderStates.size()) {
            renderStates.add(new EspRenderState());
        }

        EspRenderState renderState = renderStates.get(renderStateCount++);
        renderState.set(entity, staticColor);
        if (entity instanceof EntityPlayer) {
            playerRenderStates.put((EntityPlayer) entity, renderState);
        }
    }

    private void addVisibleRenderState(EspRenderState renderState) {
        if (visibleRenderStateCount >= visibleRenderStates.size()) {
            visibleRenderStates.add(renderState);
        }
        else {
            visibleRenderStates.set(visibleRenderStateCount, renderState);
        }
        visibleRenderStateCount++;
    }

    private int resolveStaticColor(Entity entity) {
        if (isMiauMode() && entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (Utils.isFriended(player)) return 0xFF00AA00;
            if (Utils.isEnemy(player)) return 0xFFAA0000;
            int colorMode = (int) miauColor.getInput();
            if (colorMode == 1) {
                return TeamUtils.isSameTeam(player) ? 0xFF5555FF : 0xFFFF5555;
            }
            if (colorMode == 2) {
                return Utils.mergeAlpha(ArrayListModule.getHudColorAtPosition(0.0D, 0.0D), 255);
            }
            return TeamUtils.getTeamColor(player, 1.0F).getRGB();
        }
        int rgb = teamColor.isToggled() ? Utils.getColorFromEntity(entity) : color.getColor();
        return Utils.mergeAlpha(rgb, 255);
    }

    private int resolveBaseRenderColor(EspRenderState renderState) {
        if (isMiauMode()) {
            return resolveStaticColor(renderState.entity);
        }
        if (!teamColor.isToggled() && rainbow.isToggled()) {
            return Utils.mergeAlpha(Utils.getChroma(2L, 0L), 255);
        }
        return renderState.staticColor;
    }

    private int resolveOutlineColor(EspRenderState renderState) {
        if (isRavenMode() && redOnDamage.isToggled() && renderState.entity.hurtTime != 0) {
            return 0xFFFF0000;
        }
        return renderState.renderColor;
    }

    public void render(Entity en, int rgb) {
        if (!box.isToggled() && !shaded.isToggled() && !healthBar.isToggled() && !ring.isToggled()) {
            return;
        }
        if (box.isToggled()) {
            RenderUtils.renderEntity(en, 1, 0, 0, rgb, redOnDamage.isToggled());
        }

        if (shaded.isToggled()) {
            if (ModuleManager.murderMystery == null || !ModuleManager.murderMystery.isEnabled() || ModuleManager.murderMystery.isEmpty()) {
                RenderUtils.renderEntity(en, 2, 0, 0, rgb, redOnDamage.isToggled());
            }
        }

        if (healthBar.isToggled()) {
            RenderUtils.renderEntity(en, 4, 0, 0, rgb, redOnDamage.isToggled());
        }

        if (ring.isToggled()) {
            RenderUtils.renderEntity(en, 6, 0, 0, rgb, redOnDamage.isToggled());
        }
    }

    private void renderMiauWorld(EntityPlayer player, int color, float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks - renderManager.viewerPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);

            int style = (int) miauStyle.getInput();
            if (style == 2) {
                AxisAlignedBB box = player.getEntityBoundingBox().expand(0.1D, 0.1D, 0.1D)
                        .offset(x - player.posX, y - player.posY, z - player.posZ);
                GL11.glLineWidth(1.5F);
                GL11.glEnable(GL11.GL_LINE_SMOOTH);
                GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
                RenderGlobal.drawOutlinedBoundingBox(box, color >> 16 & 255,
                        color >> 8 & 255, color & 255, color >>> 24);
            } else if (style == 4 || style == 5) {
                renderMiauBillboard(player, x, y, z, color, style == 4);
            }

            if ((int) miauHealthBar.getInput() == 2) {
                renderMiauWorldHealth(player, x, y, z);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private void renderMiauBillboard(EntityPlayer player, double x, double y, double z, int color, boolean corners) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y + player.height / 2.0D, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.scale(corners ? -0.098F : -0.1F, corners ? -0.098F : -0.1F,
                corners ? 0.098F : 0.1F);
        float width = (float) ((corners ? 26.6D : 23.3D) * player.width / 2.0D);
        float height = 12.0F;
        if (corners) {
            drawMiauQuad(width, height - 1.0F, width - 4.0F, height, color);
            drawMiauQuad(-width, height - 1.0F, -width + 4.0F, height, color);
            drawMiauQuad(-width, height, -width + 1.0F, height - 4.0F, color);
            drawMiauQuad(width, height, width - 1.0F, height - 4.0F, color);
            drawMiauQuad(width, -height, width - 4.0F, -height + 1.0F, color);
            drawMiauQuad(-width, -height, -width + 4.0F, -height + 1.0F, color);
            drawMiauQuad(-width, -height + 1.0F, -width + 1.0F, -height + 4.0F, color);
            drawMiauQuad(width, -height + 1.0F, width - 1.0F, -height + 4.0F, color);
            drawMiauQuad(width, height, width - 4.0F, height + 0.2F, 0xFF000000);
            drawMiauQuad(-width, height, -width + 4.0F, height + 0.2F, 0xFF000000);
            drawMiauQuad(-width - 0.2F, height + 0.2F, -width, height - 4.0F, 0xFF000000);
            drawMiauQuad(width + 0.2F, height + 0.2F, width, height - 4.0F, 0xFF000000);
            drawMiauQuad(width + 0.2F, -height, width - 4.0F, -height - 0.2F, 0xFF000000);
            drawMiauQuad(-width - 0.2F, -height, -width + 4.0F, -height - 0.2F, 0xFF000000);
            drawMiauQuad(-width - 0.2F, -height, -width, -height + 4.0F, 0xFF000000);
            drawMiauQuad(width + 0.2F, -height, width, -height + 4.0F, 0xFF000000);
        } else {
            drawMiauQuad(width, height, -width, height + 0.4F, color);
            drawMiauQuad(width, -height, -width, -height + 0.4F, color);
            drawMiauQuad(width, -height + 0.4F, width - 0.4F, height + 0.4F, color);
            drawMiauQuad(-width, -height + 0.4F, -width + 0.4F, height + 0.4F, color);
        }
        GlStateManager.popMatrix();
    }

    private void renderMiauWorldHealth(EntityPlayer player, double x, double y, double z) {
        float maxHealth = player.getMaxHealth();
        if (maxHealth <= 0.0F) {
            return;
        }
        float progress = Math.max(0.0F, Math.min(1.0F,
                (player.getHealth() + player.getAbsorptionAmount()) / maxHealth));
        float height = player.height + 0.2F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y - 0.1D, z);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        drawMiauQuad(0.5725F, -0.0275F, 0.7275F, height + 0.0275F, 0xFF000000);
        drawMiauQuad(0.6F, 0.0F, 0.7F, height, 0xFF404040);
        drawMiauQuad(0.6F, 0.0F, 0.7F, height * progress, miauHealthColor(progress));
        GlStateManager.popMatrix();
    }

    private static void drawMiauQuad(float left, float top, float right, float bottom, int color) {
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(left, top);
        GL11.glVertex2f(left, bottom);
        GL11.glVertex2f(right, bottom);
        GL11.glVertex2f(right, top);
        MiauDrawCalls.glEnd();
    }

    private static int miauHealthColor(float progress) {
        float clamped = Math.max(0.0F, Math.min(1.0F, progress));
        if (clamped >= 0.9F) return 0xFF00FF00;
        if (clamped >= 0.55F) return blendHealthColor(0xFFFFFF00, 0xFF00FF00, (clamped - 0.55F) / 0.35F);
        if (clamped >= 0.45F) return 0xFFFFFF00;
        if (clamped >= 0.1F) return blendHealthColor(0xFFFF0000, 0xFFFFFF00, (clamped - 0.1F) / 0.35F);
        return 0xFFFF0000;
    }

    private static int blendHealthColor(int from, int to, float progress) {
        int red = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * progress);
        int green = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * progress);
        return 0xFF000000 | red << 16 | green << 8;
    }

    private void renderTwoDPass(float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        if (renderManager == null) {
            return;
        }

        ScaledResolution scaledResolution = new ScaledResolution(mc);
        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(partialTicks, 0);
        projectionContext = RenderUtils.captureProjectionContext(projectionContext, scaledResolution.getScaleFactor());
        mc.entityRenderer.setupOverlayRendering();

        int screenWidth = scaledResolution.getScaledWidth();
        int screenHeight = scaledResolution.getScaledHeight();
        for (int i = 0; i < visibleRenderStateCount; i++) {
            renderTwoD(visibleRenderStates.get(i), renderManager, screenWidth, screenHeight, partialTicks);
        }
    }

    private void renderTwoD(EspRenderState renderState, RenderManager renderManager, int screenWidth, int screenHeight, float partialTicks) {
        EntityLivingBase en = renderState.entity;
        double playerX = en.lastTickPosX + (en.posX - en.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double playerY = en.lastTickPosY + (en.posY - en.lastTickPosY) * partialTicks - renderManager.viewerPosY;
        double playerZ = en.lastTickPosZ + (en.posZ - en.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;

        AxisAlignedBB bbox = en.getEntityBoundingBox().expand(0.1D, 0.1D, 0.1D);
        double minRelX = bbox.minX - en.posX + playerX;
        double minRelY = bbox.minY - en.posY + playerY;
        double minRelZ = bbox.minZ - en.posZ + playerZ;
        double maxRelX = bbox.maxX - en.posX + playerX;
        double maxRelY = bbox.maxY - en.posY + playerY;
        double maxRelZ = bbox.maxZ - en.posZ + playerZ;

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        boolean isInView = false;

        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? minRelX : maxRelX;
            double y = (corner & 2) == 0 ? minRelY : maxRelY;
            double z = (corner & 4) == 0 ? minRelZ : maxRelZ;

            if (RenderUtils.projectTo2D(projectionContext, x, y, z, projectedPoint)) {
                double depth = projectedPoint[2];
                if (depth >= (isMiauMode() ? 1.0D : 1.0003684D) || depth <= 0.0D) {
                    continue;
                }

                isInView = true;
                double screenX = projectedPoint[0];
                double screenY = projectedPoint[1];

                if (screenX < minX) minX = screenX;
                if (screenY < minY) minY = screenY;
                if (screenX > maxX) maxX = screenX;
                if (screenY > maxY) maxY = screenY;
            }
        }

        if (!isInView) {
            return;
        }

        if (!isMiauMode()) {
            minX = Math.max(0, minX);
            minY = Math.max(0, minY);
            maxX = Math.min(screenWidth, maxX);
            maxY = Math.min(screenHeight, maxY);
        }
        if (maxX <= minX || maxY <= minY) {
            return;
        }

        if (isMiauMode()) {
            renderMiauTwoD(renderState, minX, minY, maxX, maxY);
        } else {
            renderExhibitionTwoD(renderState, minX, minY, maxX, maxY);
        }
    }

    private void renderMiauTwoD(EspRenderState renderState, double left, double top, double right, double bottom) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

            if ((int) miauStyle.getInput() == 1) {
                int color = renderState.renderColor;
                int darkColor = color & 0xFF000000 | (color & 0x00FCFCFC) >> 2;
                drawMiauOutline2D(left, top, right, bottom, 3.0F, darkColor);
                drawMiauOutline2D(left, top, right, bottom, 1.5F, color);
            }

            if ((int) miauHealthBar.getInput() == 1) {
                EntityLivingBase player = renderState.entity;
                float maxHealth = player.getMaxHealth();
                if (maxHealth > 0.0F) {
                    float progress = Math.max(0.0F, Math.min(1.0F,
                            (player.getHealth() + player.getAbsorptionAmount()) / maxHealth));
                    double x = left - (right - left) * 0.08D;
                    drawMiauLine2D(x, top, x, bottom, 3.0F,
                            darkenMiauHealthColor(miauHealthColor(progress)));
                    drawMiauLine2D(x, bottom, x, bottom - (bottom - top) * progress,
                            1.5F, miauHealthColor(progress));
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static void drawMiauOutline2D(double left, double top, double right, double bottom,
                                            float width, int color) {
        GL11.glLineWidth(width);
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2d(left, top);
        GL11.glVertex2d(right, top);
        GL11.glVertex2d(right, bottom);
        GL11.glVertex2d(left, bottom);
        MiauDrawCalls.glEnd();
    }

    static int darkenMiauHealthColor(int color) {
        int red = (int) ((color >> 16 & 255) * 0.2F);
        int green = (int) ((color >> 8 & 255) * 0.2F);
        int blue = (int) ((color & 255) * 0.2F);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void drawMiauLine2D(double x1, double y1, double x2, double y2, float width, int color) {
        GL11.glLineWidth(width);
        GL11.glColor4f((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        MiauDrawCalls.glEnd();
    }

    public void renderSkeleton(EntityPlayer player, ModelBiped modelBiped, int color,
                               float partialTicks) {
        RenderManager renderManager = mc.getRenderManager();
        double posX = player.lastTickPosX
                + (player.posX - player.lastTickPosX) * partialTicks - renderManager.viewerPosX;
        double posY = player.lastTickPosY
                + (player.posY - player.lastTickPosY) * partialTicks - renderManager.viewerPosY;
        double posZ = player.lastTickPosZ
                + (player.posZ - player.lastTickPosZ) * partialTicks - renderManager.viewerPosZ;
        renderSkeleton(player, modelBiped, color, partialTicks, posX, posY, posZ);
    }

    private void renderSkeleton(EntityPlayer player, ModelBiped modelBiped, int color,
                                float partialTicks, double posX, double posY, double posZ) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glColor4f(
                    (color >> 16 & 0xFF) / 255.0F,
                    (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    (color >>> 24 & 0xFF) / 255.0F);
            GL11.glTranslated(posX, posY, posZ);

            float distance = mc.thePlayer.getDistanceToEntity(player);
            GL11.glLineWidth(calculateSkeletonLineWidth(distance, thickness.getInput()));
            GL11.glRotatef(-interpolateAngle(player.prevRenderYawOffset,
                    player.renderYawOffset, partialTicks), 0.0F, 1.0F, 0.0F);

            boolean sneaking = player.isSneaking();
            double legHeight = sneaking ? 0.6D : 0.75D;
            GL11.glTranslated(0.0D, legHeight, sneaking ? -0.2D : 0.0D);
            drawLine(-0.15D, 0.0D, 0.0D, 0.15D, 0.0D, 0.0D);
            drawBone(modelBiped.bipedRightLeg, -0.15D, 0.0D, 0.0D, legHeight);
            drawBone(modelBiped.bipedLeftLeg, 0.15D, 0.0D, 0.0D, legHeight);

            GL11.glPushMatrix();
            if (sneaking) {
                GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
            }
            drawLine(0.0D, 0.0D, 0.0D, 0.0D, 0.65D, 0.0D);
            GL11.glTranslated(0.0D, 0.65D, 0.0D);
            drawLine(-0.35D, 0.0D, 0.0D, 0.35D, 0.0D, 0.0D);
            drawBone(modelBiped.bipedRightArm, -0.35D, 0.0D, 0.0D, 0.6D);
            drawBone(modelBiped.bipedLeftArm, 0.35D, 0.0D, 0.0D, 0.6D);
            drawHead(modelBiped.bipedHead);
            GL11.glPopMatrix();
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawBone(ModelRenderer model, double x, double y, double z, double length) {
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        applySkeletonRotation(model);
        drawLine(0.0D, 0.0D, 0.0D, 0.0D, -length, 0.0D);
        GL11.glPopMatrix();
    }

    private void drawHead(ModelRenderer model) {
        GL11.glPushMatrix();
        applySkeletonRotation(model);
        drawLine(0.0D, 0.0D, 0.0D, 0.0D, 0.4D, 0.0D);
        drawLine(0.0D, 0.4D, 0.0D, 0.0D, 0.4D, 0.25D);
        GL11.glPopMatrix();
    }

    private void applySkeletonRotation(ModelRenderer model) {
        GL11.glRotatef(model.rotateAngleX * RAD_TO_DEG, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(-model.rotateAngleY * RAD_TO_DEG, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-model.rotateAngleZ * RAD_TO_DEG, 0.0F, 0.0F, 1.0F);
    }

    static float interpolateAngle(float previous, float current, float partialTicks) {
        float delta = (current - previous) % 360.0F;
        if (delta < -180.0F) {
            delta += 360.0F;
        } else if (delta >= 180.0F) {
            delta -= 360.0F;
        }
        return previous + partialTicks * delta;
    }

    static float calculateSkeletonLineWidth(float distance, double thickness) {
        float clampedDistance = Math.max(0.0F, Math.min(distance, 100.0F));
        float distanceWidth = Math.max(1.0F, 4.0F * (1.0F - clampedDistance / 100.0F));
        return distanceWidth * (float) Math.max(0.25D, Math.min(thickness, 2.0D));
    }

    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        MiauDrawCalls.glEnd();
    }

    private void drawRect(double left, double top, double right, double bottom, int color) {
        RenderUtils.drawRect(left, top, right, bottom, color);
    }

    private void drawBorderedRect(double x, double y, double x2, double y2, double width, int color, int borderColor) {
        drawRect(x, y, x2, y2, color);
        drawRect(x, y, x + width, y2, borderColor);
        drawRect(x2 - width, y, x2, y2, borderColor);
        drawRect(x, y, x2, y + width, borderColor);
        drawRect(x, y2 - width, x2, y2, borderColor);
    }

    private int getHealthColor(float progress) {
        float r = progress < 0.5f ? 1.0f : (1.0f - progress) * 2.0f;
        float g = progress > 0.5f ? 1.0f : progress * 2.0f;
        return new java.awt.Color(r, g, 0.0f).getRGB();
    }

    private void renderExhibitionTwoD(EspRenderState renderState, double x, double y, double endx, double endy) {
        int color = renderState.renderColor;
        net.minecraft.client.renderer.entity.RenderItem renderItem = mc.getRenderItem();

        int modeIdx = (int) boxStyle.getInput();
        double xDiff = (endx - x) / 4;
        double x2Diff = (endx - x) / (modeIdx == 2 ? 4 : 3);
        double yDiff = modeIdx == 2 ? xDiff : (endy - y) / 4;

        if (modeIdx == 0) {
            drawBorderedRect(x - 0.5, y - 0.5, endx + 0.5, endy + 0.5, 1.0, 0, 0x96000000);
            drawBorderedRect(x + 0.5, y + 0.5, endx - 0.5, endy - 0.5, 1.0, 0, color);
            drawBorderedRect(x + 1.5, y + 1.5, endx - 1.5, endy - 1.5, 1.0, 0, 0x96000000);
        } else if (modeIdx == 3) {

            drawRect(x + 0.5, y + 0.5, x + 1.5, endy - 0.5, color);
            drawRect(x - 0.5, y + 0.5, x + 0.5, endy - 0.5, 0x96000000);
            drawRect(x + 1.5, y + 2.5, x + 2.5, endy - 2.5, 0x96000000);

            drawRect(x + 1, y + 0.5, x + xDiff, y + 1.5, color);
            drawRect(x - 0.5, y - 0.5, x + xDiff, y + 0.5, 0x96000000);
            drawRect(x + 1.5, y + 1.5, x + xDiff, y + 2.5, 0x96000000);
            drawRect(x + xDiff, y - 0.5, x + xDiff + 1, y + 2.5, 0x96000000);

            drawRect(x + 1, endy - 0.5, x + xDiff, endy - 1.5, color);
            drawRect(x - 0.5, endy + 0.5, x + xDiff, endy - 0.5, 0x96000000);
            drawRect(x + 1.5, endy - 1.5, x + xDiff, endy - 2.5, 0x96000000);
            drawRect(x + xDiff, endy + 0.5, x + xDiff + 1, endy - 2.5, 0x96000000);

            drawRect(endx - 0.5, y + 0.5, endx - 1.5, endy - 0.5, color);
            drawRect(endx + 0.5, y + 0.5, endx - 0.5, endy - 0.5, 0x96000000);
            drawRect(endx - 1.5, y + 2.5, endx - 2.5, endy - 2.5, 0x96000000);

            drawRect(endx - 1, y + 0.5, endx - xDiff, y + 1.5, color);
            drawRect(endx + 0.5, y - 0.5, endx - xDiff, y + 0.5, 0x96000000);
            drawRect(endx - 1.5, y + 1.5, endx - xDiff, y + 2.5, 0x96000000);
            drawRect(endx - xDiff, y - 0.5, endx - xDiff - 1, y + 2.5, 0x96000000);

            drawRect(endx - 1, endy - 0.5, endx - xDiff, endy - 1.5, color);
            drawRect(endx + 0.5, endy + 0.5, endx - xDiff, endy - 0.5, 0x96000000);
            drawRect(endx - 1.5, endy - 1.5, endx - xDiff, endy - 2.5, 0x96000000);
            drawRect(endx - xDiff, endy + 0.5, endx - xDiff - 1, endy - 2.5, 0x96000000);
        } else {

            drawRect(x + 0.5, y + 0.5, x + 1.5, y + yDiff + 0.5, color);
            drawRect(x + 0.5, endy - 0.5, x + 1.5, endy - yDiff - 0.5, color);

            drawRect(x - 0.5, y + 0.5, x + 0.5, y + yDiff + 0.5, 0x96000000);
            drawRect(x + 1.5, y + 2.5, x + 2.5, y + yDiff + 0.5, 0x96000000);
            drawRect(x - 0.5, y + yDiff + 0.5, x + 2.5, y + yDiff + 1.5, 0x96000000);

            drawRect(x - 0.5, endy - 0.5, x + 0.5, endy - yDiff - 0.5, 0x96000000);
            drawRect(x + 1.5, endy - 2.5, x + 2.5, endy - yDiff - 0.5, 0x96000000);
            drawRect(x - 0.5, endy - yDiff - 0.5, x + 2.5, endy - yDiff - 1.5, 0x96000000);

            drawRect(x + 1, y + 0.5, x + x2Diff, y + 1.5, color);
            drawRect(x - 0.5, y - 0.5, x + x2Diff, y + 0.5, 0x96000000);
            drawRect(x + 1.5, y + 1.5, x + x2Diff, y + 2.5, 0x96000000);
            drawRect(x + x2Diff, y - 0.5, x + x2Diff + 1, y + 2.5, 0x96000000);

            drawRect(x + 1, endy - 0.5, x + x2Diff, endy - 1.5, color);
            drawRect(x - 0.5, endy + 0.5, x + x2Diff, endy - 0.5, 0x96000000);
            drawRect(x + 1.5, endy - 1.5, x + x2Diff, endy - 2.5, 0x96000000);
            drawRect(x + x2Diff, endy + 0.5, x + x2Diff + 1, endy - 2.5, 0x96000000);

            drawRect(endx - 0.5, y + 0.5, endx - 1.5, y + yDiff + 0.5, color);
            drawRect(endx - 0.5, endy - 0.5, endx - 1.5, endy - yDiff - 0.5, color);

            drawRect(endx + 0.5, y + 0.5, endx - 0.5, y + yDiff + 0.5, 0x96000000);
            drawRect(endx - 1.5, y + 2.5, endx - 2.5, y + yDiff + 0.5, 0x96000000);
            drawRect(endx + 0.5, y + yDiff + 0.5, endx - 2.5, y + yDiff + 1.5, 0x96000000);

            drawRect(endx + 0.5, endy - 0.5, endx - 0.5, endy - yDiff - 0.5, 0x96000000);
            drawRect(endx - 1.5, endy - 2.5, endx - 2.5, endy - yDiff - 0.5, 0x96000000);
            drawRect(endx + 0.5, endy - yDiff - 0.5, endx - 2.5, endy - yDiff - 1.5, 0x96000000);

            drawRect(endx - 1, y + 0.5, endx - x2Diff, y + 1.5, color);
            drawRect(endx + 0.5, y - 0.5, endx - x2Diff, y + 0.5, 0x96000000);
            drawRect(endx - 1.5, y + 1.5, endx - x2Diff, y + 2.5, 0x96000000);
            drawRect(endx - x2Diff, y - 0.5, endx - x2Diff - 1, y + 2.5, 0x96000000);

            drawRect(endx - 1, endy - 0.5, endx - x2Diff, endy - 1.5, color);
            drawRect(endx + 0.5, endy + 0.5, endx - x2Diff, endy - 0.5, 0x96000000);
            drawRect(endx - 1.5, endy - 1.5, endx - x2Diff, endy - 2.5, 0x96000000);
            drawRect(endx - x2Diff, endy + 0.5, endx - x2Diff - 1, endy - 2.5, 0x96000000);
        }

        if (healthBar.isToggled() && renderState.entity != null) {
            EntityLivingBase player = renderState.entity;
            float health = player.getHealth();
            float maxHealth = player.getMaxHealth();
            float absorption = player.getAbsorptionAmount();
            float progress = health / (maxHealth + absorption);
            float realHealthProgress = Math.min(1.0f, Math.max(0.0f, health / maxHealth));

            int healthColor = getHealthColor(realHealthProgress);
            double difference = y - (endy - 0.5);
            double healthLocation = endy + difference * progress;

            drawBorderedRect(x - 6.5, y - 0.5, x - 2.5, endy + 0.5, 1.0, 0x96000000, 0x96000000);
            int opacityColor = (healthColor & 0x00FFFFFF) | (35 << 24);
            drawRect(x - 5.5, healthLocation, x - 3.5, endy + difference, opacityColor);
            drawRect(x - 5.5, endy - 0.5, x - 3.5, healthLocation, healthColor);

            if (absorption > 0) {
                double absorptionDifferent = difference * (absorption / (maxHealth + absorption));
                drawRect(x - 5.5, healthLocation, x - 3.5, absorptionDifferent + healthLocation, 0xFFFFAA00);
            }
        }

        if (names.isToggled() && renderState.entity instanceof EntityPlayer) {
            EntityPlayer ent = (EntityPlayer) renderState.entity;
            String renderName = ent.getDisplayName().getFormattedText();
            float nameWidth = mc.fontRendererObj.getStringWidth(renderName);
            float nameX = (float) (x + (endx - x) / 2.0 - nameWidth / 2.0);
            float nameY = (float) (y - mc.fontRendererObj.FONT_HEIGHT - 2);
            mc.fontRendererObj.drawStringWithShadow(renderName, nameX, nameY, -1);
        }

        if (armor.isToggled() && renderState.entity instanceof EntityPlayer) {
            EntityPlayer ent = (EntityPlayer) renderState.entity;
            float var1_a = (float) ((endy - y) / 4);
            for (int i = 1; i <= 4; i++) {
                ItemStack stack = ent.getEquipmentInSlot(i);
                if (stack != null) {
                    double percent = 1.0 - (double) stack.getItemDamage() / (double) stack.getMaxDamage();
                    double armorYStart = y + var1_a * (4 - i);
                    double armorYEnd = y + var1_a * (5 - i);
                    drawBorderedRect(endx + 1, armorYStart + 1, endx + 5, armorYEnd, 1.0, 0x961C9CB3, 0x96000000);
                    double diff1 = (armorYEnd - (armorYStart + 2));
                    drawRect(endx + 2, armorYEnd - 1, endx + 4, armorYEnd - 1 - (diff1 * percent), 0xFF4ECEE5);
                }
            }
        }

        if ((items.isToggled() || itemIcons.isToggled()) && renderState.entity instanceof EntityPlayer) {
            EntityPlayer ent = (EntityPlayer) renderState.entity;
            ItemStack stack = ent.getCurrentEquippedItem();
            if (stack != null) {
                double itemY = endy + 2;
                if (items.isToggled()) {
                    String customName = stack.getDisplayName();
                    float nameWidth = mc.fontRendererObj.getStringWidth(customName);
                    float itemX = (float) (x + (endx - x) / 2.0 - nameWidth / 2.0);
                    mc.fontRendererObj.drawStringWithShadow(customName, itemX, (float) itemY, -1);
                    itemY += mc.fontRendererObj.FONT_HEIGHT + 2;
                }
                if (itemIcons.isToggled()) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(x + (endx - x) / 2.0 - 8.0, itemY, 0.0);
                    RenderHelper.enableGUIStandardItemLighting();
                    renderItem.renderItemAndEffectIntoGUI(stack, 0, 0);
                    renderItem.renderItemOverlays(mc.fontRendererObj, stack, 0, 0);
                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();
                }
            }
        }
    }
}
