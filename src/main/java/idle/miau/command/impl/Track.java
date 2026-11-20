package idle.miau.command.impl;

import idle.miau.command.Command;
import idle.miau.command.CommandCategory;
import idle.miau.command.CommandInput;
import idle.miau.module.ModuleManager;
import idle.miau.module.impl.world.AntiBot;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import net.minecraft.entity.player.EntityPlayer;
import idle.miau.mcp.event.client.RenderWorldLastEvent;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.event.Priority;
import idle.miau.mcp.event.Subscribe;

import idle.miau.mcp.event.world.WorldEvent;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Track extends Command {
    public final List<EntityPlayer> trackedPlayers = new ArrayList<>();

    public Track() {
        super("track", "Track players with an ESP marker.", CommandCategory.GENERAL, new String[] {"track <name>", "track clear"});
        McpRuntime.EVENTS.register(this);
    }

    @Override
    public void execute(CommandInput input) {
        if (input.argumentCount() == 0) {
            if (trackedPlayers.isEmpty()) {
                replyWithHeader("&b0 &7players tracked.");
                return;
            }

            replyWithHeader("&7Tracking &b" + trackedPlayers.size() + " &7player" + (trackedPlayers.size() == 1 ? "" : "s") + ".");
            for (EntityPlayer player : trackedPlayers) {
                replyWithHeader(" &b" + player.getName());
            }
            return;
        }

        if (input.argumentCount() != 1) {
            syntaxError();
            return;
        }

        String playerName = input.getArgument(0);
        if ("clear".equalsIgnoreCase(playerName)) {
            replyWithHeader("&b" + trackedPlayers.size() + " &7player" + (trackedPlayers.size() == 1 ? "" : "s") + " cleared.");
            trackedPlayers.clear();
            return;
        }

        EntityPlayer player = mc.theWorld.getPlayerEntityByName(playerName);
        if (player == mc.thePlayer) {
            replyWithHeader("&cYou cannot track yourself.");
            return;
        }

        if (player == null) {
            replyWithHeader("&b" + playerName + " &7not found.");
            return;
        }

        if (trackedPlayers.contains(player)) {
            trackedPlayers.remove(player);
            replyWithHeader("&7Stopped tracking &b" + playerName);
            return;
        }

        trackedPlayers.add(player);
        replyWithHeader("&7Started tracking &b" + playerName);
    }

    @Subscribe
    public void onWorldUnload(WorldEvent.Unload event) {
        trackedPlayers.clear();
    }

    @Subscribe(priority = Priority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!Utils.nullCheck() || trackedPlayers.isEmpty()) {
            return;
        }

        for (EntityPlayer player : new ArrayList<>(trackedPlayers)) {
            if (player == null || player.isDead || player.deathTime != 0) {
                continue;
            }
            if (mc.thePlayer != player && AntiBot.isBot(player)) {
                continue;
            }
            if (ModuleManager.murderMystery == null || !ModuleManager.murderMystery.isEnabled() || ModuleManager.murderMystery.isEmpty()) {
                RenderUtils.renderEntity(player, 2, 0, 0, Color.RED.getRGB(), false);
            }
        }
    }
}
