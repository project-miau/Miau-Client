package idle.miau.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.ArrayList;

public final class ServerUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private ServerUtils() {
    }

    public static ArrayList<String> getScoreboardLines() {
        ArrayList<String> lines = new ArrayList<String>();
        if (mc.theWorld == null) {
            return lines;
        }
        Scoreboard scoreboard = mc.theWorld.getScoreboard();
        if (scoreboard == null) {
            return lines;
        }
        ScoreObjective objective = scoreboard.getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return lines;
        }
        scoreboard.getSortedScores(objective).forEach(score -> lines.add(
                ScorePlayerTeam.formatPlayerName(
                        scoreboard.getPlayersTeam(score.getPlayerName()),
                        score.getPlayerName())));
        return lines;
    }

    public static boolean isHypixel() {
        ArrayList<String> lines = getScoreboardLines();
        if (lines.isEmpty()) {
            return false;
        }
        if (lines.get(0).equals("\u00A7ewww.hypixel.ne\uD83C\uDF82\u00A7et")) {
            return true;
        }
        return lines.get(0).equals("\u00A7ewww.hypixel.ne\u00A7g\u00A7et");
    }
}
