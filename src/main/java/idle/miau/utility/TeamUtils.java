package idle.miau.utility;

import idle.miau.Miau;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.awt.Color;

public final class TeamUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private TeamUtils() {
    }

    public static Color getTeamColor(EntityPlayer player, float alpha) {
        int colorCode = 0xFFFFFF;
        ScorePlayerTeam playerTeam = (ScorePlayerTeam) player.getTeam();
        if (playerTeam != null) {
            String colorPrefix = FontRenderer.getFormatFromString(playerTeam.getColorPrefix());
            if (colorPrefix.length() >= 2) {
                colorCode = mc.fontRendererObj.getColorCode(colorPrefix.charAt(1));
            }
        }
        return new Color(colorCode & 0xFFFFFF | (int) (alpha * 255.0F) << 24, true);
    }

    public static boolean isBot(EntityPlayer player) {
        if (player == mc.thePlayer) {
            return false;
        }
        NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(player.getName());
        if (playerInfo == null) {
            return true;
        }
        if (!ServerUtils.isHypixel()) {
            return false;
        }
        if (player.getName().startsWith("\u00A7k")) {
            return player.isInvisible();
        }
        if (playerInfo.getResponseTime() < 1) {
            return true;
        }
        ScorePlayerTeam playerTeam = playerInfo.getPlayerTeam();
        if (playerTeam == null) {
            return false;
        }
        if (!playerTeam.getTeamName().isEmpty()) {
            return false;
        }
        return playerTeam.getColorPrefix().equals("\u00A7c");
    }

    public static boolean isSameTeam(EntityPlayer player) {
        if (player == mc.thePlayer) {
            return true;
        }
        NetworkPlayerInfo selfInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (selfInfo == null) {
            return false;
        }
        ScorePlayerTeam selfTeam = selfInfo.getPlayerTeam();
        if (selfTeam == null) {
            return false;
        }
        NetworkPlayerInfo targetInfo = mc.getNetHandler().getPlayerInfo(player.getUniqueID());
        if (targetInfo == null) {
            return false;
        }
        ScorePlayerTeam targetTeam = targetInfo.getPlayerTeam();
        if (targetTeam == null) {
            return false;
        }
        return selfTeam.getColorPrefix().equals(targetTeam.getColorPrefix());
    }

    public static boolean isFriend(EntityPlayer player) {
        return Miau.playerRelationsManager != null
                && Miau.playerRelationsManager.isFriend(player.getName());
    }
}
