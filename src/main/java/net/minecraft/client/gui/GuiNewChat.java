package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import idle.miau.module.impl.render.Chat;
import idle.miau.module.impl.render.HudStyle;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GuiNewChat extends Gui
{
    private static final Logger logger = LogManager.getLogger();
    private final Minecraft mc;
    private final List<String> sentMessages = Lists.<String>newArrayList();
    private final List<ChatLine> chatLines = Lists.<ChatLine>newArrayList();
    private final List<ChatLine> drawnChatLines = Lists.<ChatLine>newArrayList();
    private int scrollPos;
    private boolean isScrolled;
    private final java.util.Map<ChatLine, Long> chatLineCreationTimes = new java.util.IdentityHashMap<ChatLine, Long>();
    private float animatedScrollPos = 0.0F;
    private long lastChatDrawTime = 0L;
    private String layoutKey = "";

    public GuiNewChat(Minecraft mcIn)
    {
        this.mc = mcIn;
    }

    public void drawChat(int updateCounter) {
        if (mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) return;
        syncLayout();
        if (drawnChatLines.isEmpty()) return;
        boolean open = getChatOpen();
        float chatScale = getChatScale();
        if (chatScale <= 0) return;
        MiauFontRenderer font = HudStyle.minecraftFont(mc.fontRendererObj);
        int rowHeight = lineHeight(font);
        int lineCount = getLineCount();
        long now = System.currentTimeMillis();
        float delta = lastChatDrawTime == 0 ? 0.05f : Math.min(0.1f, (now - lastChatDrawTime) / 1000f);
        lastChatDrawTime = now;
        if (Chat.isSmoothScrollActive()) {
            animatedScrollPos += (scrollPos - animatedScrollPos) * (1 - (float) Math.exp(-15 * delta));
        } else animatedScrollPos = scrollPos;
        if (Math.abs(animatedScrollPos - scrollPos) < 0.001f) animatedScrollPos = scrollPos;
        int firstLine = Math.max(0, (int) Math.floor(animatedScrollPos));
        float fraction = animatedScrollPos - firstLine;
        float width = MathHelper.ceiling_float_int(getChatWidth() / chatScale);
        List<VisibleLine> visible = Lists.newArrayList();
        for (int index = 0; firstLine + index < drawnChatLines.size() && index <= lineCount; index++) {
            ChatLine line = drawnChatLines.get(firstLine + index);
            int age = updateCounter - line.getUpdatedCounter();
            if (age >= 200 && !open) continue;
            float fade = open ? 1 : (float) MathHelper.clamp_double((1 - age / 200.0) * 10, 0, 1);
            float alpha = fade * fade * (mc.gameSettings.chatOpacity * 0.9f + 0.1f);
            if (alpha <= 3 / 255f) continue;
            float slide = 0;
            Long created = chatLineCreationTimes.get(line);
            if (created != null && Chat.getAnimationMode() != 0) {
                float progress = MathHelper.clamp_float((now - created) / (float) Math.max(1, Chat.getAnimationSpeed()), 0, 1);
                float eased = 1 - (float) Math.pow(1 - progress, 3);
                if (Chat.getAnimationMode() == 1) slide = -24 * (1 - eased);
                else alpha *= eased;
            }
            if (alpha > 3 / 255f) visible.add(new VisibleLine(line.getChatComponent().getFormattedText(),
                    slide, (fraction - index - 1) * rowHeight, alpha));
        }
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(2, 20, 0);
            GlStateManager.scale(chatScale, chatScale, 1);
            float panelTop = 0;
            float panelAlpha = 0;
            for (VisibleLine line : visible) {
                panelTop = Math.min(panelTop, line.top);
                panelAlpha = Math.max(panelAlpha, line.alpha);
            }
            float top = Math.max(-lineCount * rowHeight, panelTop);
            float alpha = panelAlpha;
            Runnable drawText = () -> {
                RenderUtils.scissorPushGui(2, new ScaledResolution(mc).getScaledHeight() - 28 - lineCount * rowHeight * chatScale,
                        (width + 4) * chatScale, lineCount * rowHeight * chatScale);
                try {
                    for (VisibleLine line : visible) {
                        if (line.top + rowHeight <= -lineCount * rowHeight || line.top >= 0) continue;
                        int textAlpha = Math.round(line.alpha / alpha * 255);
                        if (textAlpha == 0) continue;
                        font.drawString(line.text, HudStyle.paddingX() + line.slide,
                                line.top + (rowHeight - font.getFontHeight()) / 2f, textAlpha << 24 | 0xFFFFFF, false);
                    }
                } finally {
                    RenderUtils.scissorPop();
                }
            };
            if (Chat.getChatBoxStyle() == 0) {
                HudStyle.panel(new HudStyle.Panel(0, top, width + 4, -top, alpha, drawText, null), Chat.panelStyle());
            } else {
                List<HudStyle.Panel> panels = Lists.newArrayList();
                for (VisibleLine line : visible) {
                    float lineTop = Math.max(-lineCount * rowHeight, line.top);
                    float lineBottom = Math.min(0, line.top + rowHeight);
                    if (lineBottom <= lineTop) continue;
                    panels.add(new HudStyle.Panel(0, lineTop, width + 4, lineBottom - lineTop, line.alpha, () -> {
                        RenderUtils.scissorPushGui(2, new ScaledResolution(mc).getScaledHeight() - 28 - lineCount * rowHeight * chatScale,
                                (width + 4) * chatScale, lineCount * rowHeight * chatScale);
                        try {
                            font.drawString(line.text, HudStyle.paddingX() + line.slide,
                                    line.top + (rowHeight - font.getFontHeight()) / 2f, 0xFFFFFFFF, false);
                        } finally {
                            RenderUtils.scissorPop();
                        }
                    }, null));
                }
                HudStyle.panels(panels, Chat.panelStyle());
            }
            if (open && drawnChatLines.size() > lineCount) {
                float totalHeight = drawnChatLines.size() * rowHeight;
                float viewport = lineCount * rowHeight;
                float thumb = Math.max(4, viewport * viewport / totalHeight);
                float offset = animatedScrollPos / Math.max(1, drawnChatLines.size() - lineCount) * (viewport - thumb);
                RenderUtils.drawRect(-2, -offset - thumb, -1, -offset, HudStyle.accent(0, 0));
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void syncLayout() {
        String key = mc.fontRendererObj.getUnicodeFlag() + ":" + HudStyle.scale() + ":"
                + getChatWidth() + ":" + getChatScale() + ":" + HudStyle.paddingX();
        if (!layoutKey.equals(key)) {
            layoutKey = key;
            int previousScroll = scrollPos;
            refreshChat();
            scroll(previousScroll);
            animatedScrollPos = scrollPos;
        }
    }

    private static int lineHeight(MiauFontRenderer font) {
        return Math.max(1, (int) Math.ceil(font.getFontHeight() + HudStyle.paddingY() * 2));
    }

    private record VisibleLine(String text, float slide, float top, float alpha) {
    }

    public void clearChatMessages()
    {
        this.drawnChatLines.clear();
        this.chatLines.clear();
        this.sentMessages.clear();
        this.chatLineCreationTimes.clear();
    }

    public void printChatMessage(IChatComponent chatComponent)
    {
        this.printChatMessageWithOptionalDeletion(chatComponent, 0);
    }

     


    public void printChatMessageWithOptionalDeletion(IChatComponent chatComponent, int chatLineId)
    {
        this.setChatLine(chatComponent, chatLineId, this.mc.ingameGUI.getUpdateCounter(), false);
        logger.info("[CHAT] " + chatComponent.getUnformattedText());
    }

    private void setChatLine(IChatComponent chatComponent, int chatLineId, int updateCounter, boolean displayOnly)
    {
        if (chatLineId != 0)
        {
            this.deleteChatLine(chatLineId);
        }
        else if (!displayOnly)
        {
            idle.miau.patcher.chat.CompactChat.processChat(chatComponent, this.chatLines, this.drawnChatLines);
        }

        int i = MathHelper.floor_float((float)this.getChatWidth() / this.getChatScale());
        List<IChatComponent> list = GuiUtilRenderComponents.splitText(chatComponent, Math.max(1, i - Math.round(HudStyle.paddingX() * 2)), HudStyle.minecraftFont(mc.fontRendererObj), false, false);
        boolean flag = this.getChatOpen();

        for (IChatComponent ichatcomponent : list)
        {
            if (flag && this.scrollPos > 0)
            {
                this.isScrolled = true;
                this.scroll(1);
            }

            ChatLine newChatLine = new ChatLine(updateCounter, ichatcomponent, chatLineId);
            this.drawnChatLines.add(0, newChatLine);
            this.chatLineCreationTimes.put(newChatLine, System.currentTimeMillis() - (displayOnly ? Chat.getAnimationSpeed() : 0));
        }

        if (!displayOnly && chatLineId == 0)
        {
            idle.miau.patcher.chat.CompactChat.setDrawnCount(list.size());
        }

        while (this.drawnChatLines.size() > 100)
        {
            this.drawnChatLines.remove(this.drawnChatLines.size() - 1);
        }

        if (!displayOnly)
        {
            this.chatLines.add(0, new ChatLine(updateCounter, chatComponent, chatLineId));

            while (this.chatLines.size() > 100)
            {
                this.chatLines.remove(this.chatLines.size() - 1);
            }
        }
    }

    public void refreshChat()
    {
        this.drawnChatLines.clear();
        this.chatLineCreationTimes.clear();
        this.resetScroll();

        for (int i = this.chatLines.size() - 1; i >= 0; --i)
        {
            ChatLine chatline = (ChatLine)this.chatLines.get(i);
            this.setChatLine(chatline.getChatComponent(), chatline.getChatLineID(), chatline.getUpdatedCounter(), true);
        }
    }

    public List<String> getSentMessages()
    {
        return this.sentMessages;
    }

     




    public void addToSentMessages(String message)
    {
        if (this.sentMessages.isEmpty() || !((String)this.sentMessages.get(this.sentMessages.size() - 1)).equals(message))
        {
            this.sentMessages.add(message);
        }
    }

     


    public void resetScroll()
    {
        this.scrollPos = 0;
        this.animatedScrollPos = 0;
        this.isScrolled = false;
    }

     




    public void scroll(int amount)
    {
        this.scrollPos += amount;
        int i = this.drawnChatLines.size();

        if (this.scrollPos > i - this.getLineCount())
        {
            this.scrollPos = i - this.getLineCount();
        }

        if (this.scrollPos <= 0)
        {
            this.scrollPos = 0;
            this.isScrolled = false;
        }
    }

     





    public IChatComponent getChatComponent(int mouseX, int mouseY) {
        if (!getChatOpen() || getChatScale() <= 0) return null;
        syncLayout();
        MiauFontRenderer font = HudStyle.minecraftFont(mc.fontRendererObj);
        float factor = new ScaledResolution(mc).getScaleFactor();
        float x = (mouseX / factor - 2) / getChatScale() - HudStyle.paddingX() - HudStyle.textOffsetX();
        float y = (mouseY / factor - 28) / getChatScale() + HudStyle.textOffsetY();
        if (x < 0 || x > getChatWidth() / getChatScale() || y < 0 || y >= getLineCount() * lineHeight(font)) return null;
        float currentScroll = Chat.isSmoothScrollActive() ? animatedScrollPos : scrollPos;
        int index = (int) Math.floor(y / lineHeight(font) + currentScroll);
        if (index < 0 || index >= drawnChatLines.size()) return null;
        int width = 0;
        for (IChatComponent component : drawnChatLines.get(index).getChatComponent()) {
            if (component instanceof ChatComponentText) {
                String text = component.getChatStyle().getFormattingCode() + component.getUnformattedTextForChat();
                width += font.getStringWidth(GuiUtilRenderComponents.func_178909_a(text, false));
                if (width > x) return component;
            }
        }
        return null;
    }

    public boolean getChatOpen()
    {
        return this.mc.currentScreen instanceof GuiChat;
    }

     




    public void deleteChatLine(int id)
    {
        Iterator<ChatLine> iterator = this.drawnChatLines.iterator();

        while (iterator.hasNext())
        {
            ChatLine chatline = (ChatLine)iterator.next();

            if (chatline.getChatLineID() == id)
            {
                iterator.remove();
            }
        }

        iterator = this.chatLines.iterator();

        while (iterator.hasNext())
        {
            ChatLine chatline1 = (ChatLine)iterator.next();

            if (chatline1.getChatLineID() == id)
            {
                iterator.remove();
                break;
            }
        }
    }

    public int getChatWidth()
    {
        return calculateChatboxWidth(this.mc.gameSettings.chatWidth);
    }

    public int getChatHeight()
    {
        return calculateChatboxHeight(this.getChatOpen() ? this.mc.gameSettings.chatHeightFocused : this.mc.gameSettings.chatHeightUnfocused);
    }

     


    public float getChatScale()
    {
        return this.mc.gameSettings.chatScale;
    }

    public static int calculateChatboxWidth(float scale)
    {
        int i = 320;
        int j = 40;
        return MathHelper.floor_float(scale * (float)(i - j) + (float)j);
    }

    public static int calculateChatboxHeight(float scale)
    {
        int i = 180;
        int j = 20;
        return MathHelper.floor_float(scale * (float)(i - j) + (float)j);
    }

    public int getLineCount()
    {
        return Math.max(1, this.getChatHeight() / lineHeight(HudStyle.minecraftFont(mc.fontRendererObj)));
    }
}
