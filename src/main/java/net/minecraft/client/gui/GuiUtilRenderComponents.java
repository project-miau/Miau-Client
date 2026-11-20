package net.minecraft.client.gui;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.Minecraft;
import idle.miau.utility.font.MiauFontRenderer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public class GuiUtilRenderComponents
{
    public static String func_178909_a(String p_178909_0_, boolean p_178909_1_)
    {
        return !p_178909_1_ && !Minecraft.getMinecraft().gameSettings.chatColours ? EnumChatFormatting.getTextWithoutFormattingCodes(p_178909_0_) : p_178909_0_;
    }

    public static List<IChatComponent> splitText(IChatComponent p_178908_0_, int p_178908_1_, FontRenderer p_178908_2_, boolean p_178908_3_, boolean p_178908_4_)
    {
        return splitText(p_178908_0_, p_178908_1_, new TextMetrics() {
            public int width(String text) { return p_178908_2_.getStringWidth(text); }
            public String trim(String text, int width) { return p_178908_2_.trimStringToWidth(text, width, false); }
        }, p_178908_3_, p_178908_4_);
    }

    public static List<IChatComponent> splitText(IChatComponent component, int width, MiauFontRenderer font,
                                                boolean trimSpaces, boolean keepColors) {
        return splitText(component, width, new TextMetrics() {
            public int width(String text) { return font.getStringWidth(text); }
            public String trim(String text, int maximum) { return font.trimStringToWidth(text, maximum); }
        }, trimSpaces, keepColors);
    }

    private interface TextMetrics {
        int width(String text);
        String trim(String text, int width);
    }

    private static List<IChatComponent> splitText(IChatComponent p_178908_0_, int p_178908_1_, TextMetrics metrics,
                                                  boolean p_178908_3_, boolean p_178908_4_) {
        p_178908_1_ = Math.max(1, p_178908_1_);
        int i = 0;
        IChatComponent ichatcomponent = new ChatComponentText("");
        List<IChatComponent> list = Lists.<IChatComponent>newArrayList();
        List<IChatComponent> list1 = Lists.newArrayList(p_178908_0_);

        for (int j = 0; j < ((List)list1).size(); ++j)
        {
            IChatComponent ichatcomponent1 = (IChatComponent)list1.get(j);
            String s = ichatcomponent1.getUnformattedTextForChat();
            boolean flag = false;

            if (s.contains("\n"))
            {
                int k = s.indexOf(10);
                String s1 = s.substring(k + 1);
                s = s.substring(0, k + 1);
                ChatComponentText chatcomponenttext = new ChatComponentText(s1);
                chatcomponenttext.setChatStyle(ichatcomponent1.getChatStyle().createShallowCopy());
                list1.add(j + 1, chatcomponenttext);
                flag = true;
            }

            String s4 = func_178909_a(ichatcomponent1.getChatStyle().getFormattingCode() + s, p_178908_4_);
            String s5 = s4.endsWith("\n") ? s4.substring(0, s4.length() - 1) : s4;
            int i1 = metrics.width(s5);
            ChatComponentText chatcomponenttext1 = new ChatComponentText(s5);
            chatcomponenttext1.setChatStyle(ichatcomponent1.getChatStyle().createShallowCopy());

            if (i + i1 > p_178908_1_)
            {
                String s2 = metrics.trim(s4, p_178908_1_ - i);
                if (i == 0 && metrics.width(s2) == 0 && s2.length() < s5.length()) {
                    int end = s2.length();
                    while (end + 1 < s5.length() && s5.charAt(end) == '\u00A7') end += 2;
                    if (end < s5.length()) end += Character.charCount(s5.codePointAt(end));
                    s2 = s5.substring(0, end);
                }
                String s3 = s2.length() < s4.length() ? s4.substring(s2.length()) : null;

                if (s3 != null && s3.length() > 0)
                {
                    int l = s2.lastIndexOf(" ");

                    if (l >= 0 && metrics.width(s4.substring(0, l)) > 0)
                    {
                        s2 = s4.substring(0, l);

                        if (p_178908_3_)
                        {
                            ++l;
                        }

                        s3 = s4.substring(l);
                    }
                    else if (i > 0 && !s4.contains(" "))
                    {
                        s2 = "";
                        s3 = s4;
                    }

                    ChatComponentText chatcomponenttext2 = new ChatComponentText(FontRenderer.getFormatFromString(s2) + s3);
                    chatcomponenttext2.setChatStyle(ichatcomponent1.getChatStyle().createShallowCopy());
                    list1.add(j + 1, chatcomponenttext2);
                }

                i1 = metrics.width(s2);
                chatcomponenttext1 = new ChatComponentText(s2);
                chatcomponenttext1.setChatStyle(ichatcomponent1.getChatStyle().createShallowCopy());
                flag = true;
            }

            if (i + i1 <= p_178908_1_ || i == 0)
            {
                i += i1;
                ichatcomponent.appendSibling(chatcomponenttext1);
            }
            else
            {
                flag = true;
            }

            if (flag)
            {
                list.add(ichatcomponent);
                i = 0;
                ichatcomponent = new ChatComponentText("");
            }
        }

        list.add(ichatcomponent);
        return list;
    }
}