package idle.miau.patcher.chat;

import idle.miau.module.impl.other.Patcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.List;

public class CompactChat {
    private static String lastMessage = "";
    private static int sameMessageCount = 1;
    private static long lastMessageTime = 0L;
    private static int lastDrawnCount = 1;

    public static void processChat(IChatComponent chatComponent, List<ChatLine> chatLines, List<ChatLine> drawnChatLines) {
        if (!Patcher.isCompactChatActive() || chatComponent == null) {
            reset();
            return;
        }

        String rawText = chatComponent.getUnformattedText().trim();
        if (rawText.isEmpty() || isDivider(rawText)) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        if (rawText.equals(lastMessage) && (now - lastMessageTime) < 15000L) {
            sameMessageCount++;
            lastMessageTime = now;

            if (!chatLines.isEmpty()) {
                chatLines.remove(0);
            }
            for (int i = 0; i < lastDrawnCount && !drawnChatLines.isEmpty(); i++) {
                drawnChatLines.remove(0);
            }

            chatComponent.appendSibling(new ChatComponentText(" \u00a77(x" + sameMessageCount + ")"));
        } else {
            lastMessage = rawText;
            sameMessageCount = 1;
            lastMessageTime = now;
        }
    }

    public static void setDrawnCount(int count) {
        lastDrawnCount = count;
    }

    public static void reset() {
        lastMessage = "";
        sameMessageCount = 1;
        lastMessageTime = 0L;
        lastDrawnCount = 1;
    }

    private static boolean isDivider(String clean) {
        if (clean.length() < 5) {
            return false;
        }
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c != '-' && c != '=' && c != '\u25AC' && c != '_') {
                return false;
            }
        }
        return true;
    }
}
