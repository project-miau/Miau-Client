package idle.miau.patcher.culling;

import idle.miau.module.impl.other.Patcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class EntityCulling {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ConcurrentHashMap<UUID, OcclusionQuery> queries = new ConcurrentHashMap<>();
    private static final boolean SUPPORT_NEW_GL;
    private static boolean supported = true;
    public static boolean renderingSpawnerEntity = false;
    private static int destroyTimer = 0;

    static {
        boolean gl33 = false;
        try {
            gl33 = GLContext.getCapabilities().OpenGL33;
        } catch (Throwable ignored) {
        }
        SUPPORT_NEW_GL = gl33;
    }

    public static boolean isSupported() {
        return supported;
    }

    public static void clear() {
        for (OcclusionQuery query : queries.values()) {
            if (query.nextQuery != 0) {
                try {
                    GL15.glDeleteQueries(query.nextQuery);
                } catch (Throwable ignored) {
                }
            }
        }
        queries.clear();
    }

    public static boolean shouldCull(EntityLivingBase entity) {
        if (!supported || !Patcher.isEntityCullingActive()) {
            return false;
        }

        if (entity == mc.thePlayer || entity.worldObj != mc.thePlayer.worldObj) {
            return false;
        }

        boolean armorstand = entity instanceof EntityArmorStand;
        if (armorstand && ((EntityArmorStand) entity).hasMarker()) {
            return false;
        }

        if (entity.isInvisibleToPlayer(mc.thePlayer) && !armorstand) {
            return false;
        }

        if (entity instanceof EntityWither || entity instanceof EntityDragon) {
            return false;
        }

        if (entity.ridingEntity != null && entity.ridingEntity == mc.thePlayer) {
            return false;
        }
        if (entity.riddenByEntity != null && entity.riddenByEntity == mc.thePlayer) {
            return false;
        }

        return checkEntity(entity);
    }

    public static boolean renderItem(Entity stack) {
        if (!supported || !Patcher.isEntityCullingActive() || stack == null || mc.thePlayer == null) {
            return false;
        }
        if (stack.worldObj != mc.thePlayer.worldObj) {
            return false;
        }
        return checkEntity(stack);
    }

    private static int getQuery() {
        try {
            return GL15.glGenQueries();
        } catch (Throwable t) {
            supported = false;
            return 0;
        }
    }

    private static boolean checkEntity(Entity entity) {
        if (renderingSpawnerEntity) {
            return false;
        }

        RenderManager rm = mc.getRenderManager();
        if (rm == null) {
            return false;
        }

        OcclusionQuery query = queries.computeIfAbsent(entity.getUniqueID(), OcclusionQuery::new);
        if (query.refresh) {
            query.nextQuery = getQuery();
            if (query.nextQuery == 0) {
                return false;
            }
            query.refresh = false;
            int mode = SUPPORT_NEW_GL ? GL33.GL_ANY_SAMPLES_PASSED : GL15.GL_SAMPLES_PASSED;

            GL15.glBeginQuery(mode, query.nextQuery);
            drawSelectionBoundingBox(entity.getEntityBoundingBox()
                    .expand(0.2, 0.2, 0.2)
                    .offset(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ)
            );
            GL15.glEndQuery(mode);
        }

        return query.occluded;
    }

    public static void drawSelectionBoundingBox(AxisAlignedBB b) {
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.colorMask(false, false, false, false);
        final Tessellator tessellator = Tessellator.getInstance();
        final WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION);
        worldrenderer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
        worldrenderer.pos(b.maxX, b.maxY, b.minZ).endVertex();
        worldrenderer.pos(b.minX, b.maxY, b.maxZ).endVertex();
        worldrenderer.pos(b.minX, b.maxY, b.minZ).endVertex();
        worldrenderer.pos(b.minX, b.minY, b.maxZ).endVertex();
        worldrenderer.pos(b.minX, b.minY, b.minZ).endVertex();
        worldrenderer.pos(b.minX, b.maxY, b.minZ).endVertex();
        worldrenderer.pos(b.minX, b.minY, b.minZ).endVertex();
        worldrenderer.pos(b.maxX, b.maxY, b.minZ).endVertex();
        worldrenderer.pos(b.maxX, b.minY, b.minZ).endVertex();
        worldrenderer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
        worldrenderer.pos(b.maxX, b.minY, b.maxZ).endVertex();
        worldrenderer.pos(b.minX, b.maxY, b.maxZ).endVertex();
        worldrenderer.pos(b.minX, b.minY, b.maxZ).endVertex();
        tessellator.draw();
        worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        worldrenderer.pos(b.minX, b.maxY, b.minZ).endVertex();
        worldrenderer.pos(b.minX, b.maxY, b.maxZ).endVertex();
        worldrenderer.pos(b.maxX, b.maxY, b.maxZ).endVertex();
        worldrenderer.pos(b.maxX, b.maxY, b.minZ).endVertex();
        worldrenderer.pos(b.minX, b.minY, b.maxZ).endVertex();
        worldrenderer.pos(b.minX, b.minY, b.minZ).endVertex();
        worldrenderer.pos(b.maxX, b.minY, b.minZ).endVertex();
        worldrenderer.pos(b.maxX, b.minY, b.maxZ).endVertex();
        tessellator.draw();
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableAlpha();
    }

    public static boolean canRenderName(EntityLivingBase entity) {
        final EntityPlayerSP player = mc.thePlayer;
        if (player == null) return false;
        if (entity instanceof EntityPlayer && entity != player) {
            final Team otherEntityTeam = entity.getTeam();
            final Team playerTeam = player.getTeam();

            if (otherEntityTeam != null) {
                final Team.EnumVisible teamVisibilityRule = otherEntityTeam.getNameTagVisibility();

                switch (teamVisibilityRule) {
                    case NEVER:
                        return false;
                    case HIDE_FOR_OTHER_TEAMS:
                        return playerTeam == null || otherEntityTeam.isSameTeam(playerTeam);
                    case HIDE_FOR_OWN_TEAM:
                        return playerTeam == null || !otherEntityTeam.isSameTeam(playerTeam);
                    case ALWAYS:
                    default:
                        return true;
                }
            }
        }

        return Minecraft.isGuiEnabled()
                && entity != mc.getRenderManager().livingPlayer
                && ((entity instanceof EntityArmorStand) || !entity.isInvisibleToPlayer(player))
                && entity.riddenByEntity == null;
    }

    public static void onRenderTick() {
        if (!supported || !Patcher.isEntityCullingActive()) {
            return;
        }

        long delay = Patcher.getCullInterval();
        long nanoTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
        for (OcclusionQuery query : queries.values()) {
            if (query.nextQuery != 0) {
                final int queryObject = GL15.glGetQueryObjecti(query.nextQuery, GL15.GL_QUERY_RESULT_AVAILABLE);
                if (queryObject != 0) {
                    query.occluded = GL15.glGetQueryObjecti(query.nextQuery, GL15.GL_QUERY_RESULT) == 0;
                    GL15.glDeleteQueries(query.nextQuery);
                    query.nextQuery = 0;
                }
            }
            if (query.nextQuery == 0 && nanoTime - query.executionTime > delay) {
                query.executionTime = nanoTime;
                query.refresh = true;
            }
        }
    }

    public static void onClientTick() {
        if (!supported || !Patcher.isEntityCullingActive()) {
            return;
        }

        if (++destroyTimer < 100) {
            return;
        }
        destroyTimer = 0;

        WorldClient theWorld = mc.theWorld;
        if (theWorld == null) {
            clear();
            return;
        }

        Set<UUID> loaded = new HashSet<>();
        for (Entity entity : theWorld.loadedEntityList) {
            loaded.add(entity.getUniqueID());
        }

        List<UUID> toRemove = new ArrayList<>();
        for (OcclusionQuery value : queries.values()) {
            if (loaded.contains(value.uuid)) {
                continue;
            }
            toRemove.add(value.uuid);
            if (value.nextQuery != 0) {
                try {
                    GL15.glDeleteQueries(value.nextQuery);
                } catch (Throwable ignored) {
                }
            }
        }

        for (UUID uuid : toRemove) {
            queries.remove(uuid);
        }
    }

    private static class OcclusionQuery {
        private final UUID uuid;
        private int nextQuery;
        private boolean refresh = true;
        private boolean occluded;
        private long executionTime = 0;

        public OcclusionQuery(UUID uuid) {
            this.uuid = uuid;
        }
    }
}
