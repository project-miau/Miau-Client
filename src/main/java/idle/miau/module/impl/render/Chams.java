package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.utility.Utils;
import net.minecraft.entity.Entity;
import idle.miau.mcp.event.client.RenderPlayerEvent.Post;
import idle.miau.mcp.event.client.RenderPlayerEvent.Pre;
import idle.miau.mcp.event.Subscribe;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;

public class Chams extends Module {
    private final GroupSetting targets = new GroupSetting("Targets");
    private ButtonSetting ignoreBots;
    private ButtonSetting renderSelf;
    private ButtonSetting hidePlayers;
    private ButtonSetting showTeammate;
    private HashSet<Entity> bots = new HashSet<>();

    public Chams() {
        super("Chams", Module.category.render, 0);
        setDescription("Changes player rendering to show them through walls.");
        this.registerSetting(targets);
        this.registerSetting(ignoreBots = new ButtonSetting(targets, "Ignore bots", false));
        this.registerSetting(renderSelf = new ButtonSetting(targets, "Render self", false));
        this.registerSetting(showTeammate = new ButtonSetting(targets, "Show Teammate", true));
        this.registerSetting(hidePlayers = new ButtonSetting("Hide players", false));
    }

    @Subscribe
    public void onPreRender(Pre e) {
        Entity entity = e.entity;
        if (entity == mc.thePlayer && (!renderSelf.isToggled() || mc.currentScreen != null)) {
            return;
        }
        if (entity != mc.thePlayer && !showTeammate.isToggled() && Utils.isTeammate(entity)) {
            return;
        }
        if (hidePlayers.isToggled() && !(entity == mc.thePlayer && renderSelf.isToggled())) {
            e.setCanceled(true);
            return;
        }
        if (ignoreBots.isToggled()) {
            if (AntiBot.isBot(entity)) {
                return;
            }
            bots.add(entity);
        }
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0f, -4_000_000.0f);
    }

    @Subscribe
    public void onPostRender(Post e) {
        Entity entity = e.entity;
        if (entity == mc.thePlayer && (!renderSelf.isToggled() || mc.currentScreen != null)) {
            return;
        }
        if (entity != mc.thePlayer && !showTeammate.isToggled() && Utils.isTeammate(entity)) {
            return;
        }
        if (hidePlayers.isToggled() && !(entity == mc.thePlayer && renderSelf.isToggled())) {
            return;
        }
        if (ignoreBots.isToggled()) {
            if (!bots.contains(entity)) {
                return;
            }
            bots.remove(entity);
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0f, 4_000_000.0f);
    }

    @Override
    public void onDisable() {
        bots.clear();
    }
}
