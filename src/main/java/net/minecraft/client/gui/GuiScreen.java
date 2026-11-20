package net.minecraft.client.gui;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import idle.miau.utility.system.ClipboardBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityList;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.event.HoverEvent.Action;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import idle.miau.Miau;
import idle.miau.utility.irc.MiauIRC;
import idle.miau.event.KeyPressEvent;
import idle.miau.mcp.McpRuntime;
import idle.miau.mcp.event.client.GuiScreenEvent.ActionPerformedEvent.Pre;
import idle.miau.mcp.event.client.GuiScreenEvent.InitGuiEvent.Post;
import idle.miau.mcp.accessor.IAccessorGuiScreen;
import idle.miau.mcp.callback.CallbackInfo;

public abstract class GuiScreen extends Gui implements GuiYesNoCallback, IAccessorGuiScreen {
   private static final Logger LOGGER = LogManager.getLogger();
   private static final Set<String> PROTOCOLS = Sets.newHashSet(new String[]{"http", "https"});
   private static final Splitter NEWLINE_SPLITTER = Splitter.on('\n');
   protected Minecraft mc;
   protected RenderItem itemRender;
   public int width;
   public int height;
   protected List<GuiButton> buttonList = Lists.newArrayList();
   protected List<GuiLabel> labelList = Lists.newArrayList();
   public boolean allowUserInput;
   protected FontRenderer fontRendererObj;
   private GuiButton selectedButton;
   private int eventButton;
   private long lastMouseEvent;
   private int touchValue;
   private URI clickedLinkURI;

   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      if (idle.miau.mcp.McpRuntime.EVENTS.post(new idle.miau.mcp.event.client.GuiScreenEvent.DrawScreenEvent.Pre(this, mouseX, mouseY, partialTicks))) {
         return;
      }
      for(int i = 0; i < this.buttonList.size(); ++i) {
         ((GuiButton)this.buttonList.get(i)).drawButton(this.mc, mouseX, mouseY);
      }

      for(int j = 0; j < this.labelList.size(); ++j) {
         ((GuiLabel)this.labelList.get(j)).drawLabel(this.mc, mouseX, mouseY);
      }
      idle.miau.mcp.McpRuntime.EVENTS.post(new idle.miau.mcp.event.client.GuiScreenEvent.DrawScreenEvent.Post(this, mouseX, mouseY, partialTicks));
   }

   public final void pressKey(char typedChar, int keyCode) throws IOException {
      keyTyped(typedChar, keyCode);
   }

   protected void keyTyped(char typedChar, int keyCode) throws IOException {
      if(keyCode == 1) {
         this.mc.displayGuiScreen((GuiScreen)null);
         if(this.mc.currentScreen == null) {
            this.mc.setIngameFocus();
         }
      }

   }

   public static String getClipboardString() {
      return ClipboardBridge.getString();
   }

   public static void setClipboardString(String copyText) {
      if(copyText != null && !copyText.isEmpty()) {
         ClipboardBridge.setString(copyText);
      }
   }

   protected void renderToolTip(ItemStack stack, int x, int y) {
      List<String> list = stack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);

      for(int i = 0; i < list.size(); ++i) {
         if(i == 0) {
            list.set(i, stack.getRarity().rarityColor + (String)list.get(i));
         } else {
            list.set(i, EnumChatFormatting.GRAY + (String)list.get(i));
         }
      }

      this.drawHoveringText(list, x, y);
   }

   protected void drawCreativeTabHoveringText(String tabName, int mouseX, int mouseY) {
      this.drawHoveringText(Arrays.asList(new String[]{tabName}), mouseX, mouseY);
   }

   protected void drawHoveringText(List<String> textLines, int x, int y) {
      if(!textLines.isEmpty()) {
         GlStateManager.disableRescaleNormal();
         RenderHelper.disableStandardItemLighting();
         GlStateManager.disableLighting();
         GlStateManager.disableDepth();
         int i = 0;

         for(String s : textLines) {
            int j = this.fontRendererObj.getStringWidth(s);
            if(j > i) {
               i = j;
            }
         }

         int l1 = x + 12;
         int i2 = y - 12;
         int k = 8;
         if(textLines.size() > 1) {
            k += 2 + (textLines.size() - 1) * 10;
         }

         if(l1 + i > this.width) {
            l1 -= 28 + i;
         }

         if(i2 + k + 6 > this.height) {
            i2 = this.height - k - 6;
         }

         this.zLevel = 300.0F;
         this.itemRender.zLevel = 300.0F;
         int l = -267386864;
         this.drawGradientRect(l1 - 3, i2 - 4, l1 + i + 3, i2 - 3, l, l);
         this.drawGradientRect(l1 - 3, i2 + k + 3, l1 + i + 3, i2 + k + 4, l, l);
         this.drawGradientRect(l1 - 3, i2 - 3, l1 + i + 3, i2 + k + 3, l, l);
         this.drawGradientRect(l1 - 4, i2 - 3, l1 - 3, i2 + k + 3, l, l);
         this.drawGradientRect(l1 + i + 3, i2 - 3, l1 + i + 4, i2 + k + 3, l, l);
         int i1 = 1347420415;
         int j1 = (i1 & 16711422) >> 1 | i1 & -16777216;
         this.drawGradientRect(l1 - 3, i2 - 3 + 1, l1 - 3 + 1, i2 + k + 3 - 1, i1, j1);
         this.drawGradientRect(l1 + i + 2, i2 - 3 + 1, l1 + i + 3, i2 + k + 3 - 1, i1, j1);
         this.drawGradientRect(l1 - 3, i2 - 3, l1 + i + 3, i2 - 3 + 1, i1, i1);
         this.drawGradientRect(l1 - 3, i2 + k + 2, l1 + i + 3, i2 + k + 3, j1, j1);

         for(int k1 = 0; k1 < textLines.size(); ++k1) {
            String s1 = (String)textLines.get(k1);
            this.fontRendererObj.drawStringWithShadow(s1, (float)l1, (float)i2, -1);
            if(k1 == 0) {
               i2 += 2;
            }

            i2 += 10;
         }

         this.zLevel = 0.0F;
         this.itemRender.zLevel = 0.0F;
         GlStateManager.enableLighting();
         GlStateManager.enableDepth();
         RenderHelper.enableStandardItemLighting();
         GlStateManager.enableRescaleNormal();
      }

   }

   protected void handleComponentHover(IChatComponent component, int x, int y) {
      if(component != null && component.getChatStyle().getChatHoverEvent() != null) {
         HoverEvent hoverevent = component.getChatStyle().getChatHoverEvent();
         if(hoverevent.getAction() == Action.SHOW_ITEM) {
            ItemStack itemstack = null;

            try {
               NBTBase nbtbase = JsonToNBT.getTagFromJson(hoverevent.getValue().getUnformattedText());
               if(nbtbase instanceof NBTTagCompound) {
                  itemstack = ItemStack.loadItemStackFromNBT((NBTTagCompound)nbtbase);
               }
            } catch (NBTException var11) {
               ;
            }

            if(itemstack != null) {
               this.renderToolTip(itemstack, x, y);
            } else {
               this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid Item!", x, y);
            }
         } else if(hoverevent.getAction() == Action.SHOW_ENTITY) {
            if(this.mc.gameSettings.advancedItemTooltips) {
               try {
                  NBTBase nbtbase1 = JsonToNBT.getTagFromJson(hoverevent.getValue().getUnformattedText());
                  if(nbtbase1 instanceof NBTTagCompound) {
                     List<String> list1 = Lists.newArrayList();
                     NBTTagCompound nbttagcompound = (NBTTagCompound)nbtbase1;
                     list1.add(nbttagcompound.getString("name"));
                     if(nbttagcompound.hasKey("type", 8)) {
                        String s = nbttagcompound.getString("type");
                        list1.add("Type: " + s + " (" + EntityList.getIDFromString(s) + ")");
                     }

                     list1.add(nbttagcompound.getString("id"));
                     this.drawHoveringText(list1, x, y);
                  } else {
                     this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid Entity!", x, y);
                  }
               } catch (NBTException var10) {
                  this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid Entity!", x, y);
               }
            }
         } else if(hoverevent.getAction() == Action.SHOW_TEXT) {
            this.drawHoveringText(NEWLINE_SPLITTER.splitToList(hoverevent.getValue().getFormattedText()), x, y);
         } else if(hoverevent.getAction() == Action.SHOW_ACHIEVEMENT) {
            StatBase statbase = StatList.getOneShotStat(hoverevent.getValue().getUnformattedText());
            if(statbase != null) {
               IChatComponent ichatcomponent = statbase.getStatName();
               IChatComponent ichatcomponent1 = new ChatComponentTranslation("stats.tooltip.type." + (statbase.isAchievement()?"achievement":"statistic"), new Object[0]);
               ichatcomponent1.getChatStyle().setItalic(Boolean.valueOf(true));
               String s1 = statbase instanceof Achievement?((Achievement)statbase).getDescription():null;
               List<String> list = Lists.newArrayList(new String[]{ichatcomponent.getFormattedText(), ichatcomponent1.getFormattedText()});
               if(s1 != null) {
                  list.addAll(this.fontRendererObj.listFormattedStringToWidth(s1, 150));
               }

               this.drawHoveringText(list, x, y);
            } else {
               this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid statistic/achievement!", x, y);
            }
         }

         GlStateManager.disableLighting();
      }

   }

   protected void setText(String newChatText, boolean shouldOverwrite) {
   }

   protected boolean handleComponentClick(IChatComponent component) {
      if(component == null) {
         return false;
      } else {
         ClickEvent clickevent = component.getChatStyle().getChatClickEvent();
         if(isShiftKeyDown()) {
            if(component.getChatStyle().getInsertion() != null) {
               this.setText(component.getChatStyle().getInsertion(), false);
            }
         } else if(clickevent != null) {
            if(clickevent.getAction() == net.minecraft.event.ClickEvent.Action.OPEN_URL) {
               if(!this.mc.gameSettings.chatLinks) {
                  return false;
               }

               try {
                  URI uri = new URI(clickevent.getValue());
                  String s = uri.getScheme();
                  if(s == null) {
                     throw new URISyntaxException(clickevent.getValue(), "Missing protocol");
                  }

                  if(!PROTOCOLS.contains(s.toLowerCase())) {
                     throw new URISyntaxException(clickevent.getValue(), "Unsupported protocol: " + s.toLowerCase());
                  }

                  if(this.mc.gameSettings.chatLinksPrompt) {
                     this.clickedLinkURI = uri;
                     this.mc.displayGuiScreen(new GuiConfirmOpenLink(this, clickevent.getValue(), 31102009, false));
                  } else {
                     this.openWebLink(uri);
                  }
               } catch (URISyntaxException var5) {
                  LOGGER.error("Can\'t open url for " + clickevent, var5);
               }
            } else if(clickevent.getAction() == net.minecraft.event.ClickEvent.Action.OPEN_FILE) {
               URI uri1 = (new File(clickevent.getValue())).toURI();
               this.openWebLink(uri1);
            } else if(clickevent.getAction() == net.minecraft.event.ClickEvent.Action.SUGGEST_COMMAND) {
               this.setText(clickevent.getValue(), true);
            } else if(clickevent.getAction() == net.minecraft.event.ClickEvent.Action.RUN_COMMAND) {
               this.sendChatMessage(clickevent.getValue(), false);
            } else {
               LOGGER.error("Don\'t know how to handle " + clickevent);
            }

            return true;
         }

         return false;
      }
   }

   public void sendChatMessage(String msg) {
      this.sendChatMessage(msg, true);
   }

   public void sendChatMessage(String msg, boolean addToChat) {
      CallbackInfo callbackInfo3 = new CallbackInfo("sendChatMessage", true);
      this.mimeow$messageSend(msg, addToChat, callbackInfo3);
      if(!callbackInfo3.isCancelled()) {
         if(addToChat) {
            this.mc.ingameGUI.getChatGUI().addToSentMessages(msg);
         }

         this.mc.thePlayer.sendChatMessage(msg);
      }
   }

   protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if(mouseButton == 0) {
         for(int i = 0; i < this.buttonList.size(); ++i) {
            GuiButton guibutton = (GuiButton)this.buttonList.get(i);
            if(guibutton.mousePressed(this.mc, mouseX, mouseY)) {
               this.selectedButton = guibutton;
               guibutton.playPressSound(this.mc.getSoundHandler());
               this.mimeow$redirect$actionPerformed(this, guibutton);
            }
         }
      }

   }

   protected void mouseReleased(int mouseX, int mouseY, int state) {
      if(this.selectedButton != null && state == 0) {
         this.selectedButton.mouseReleased(mouseX, mouseY);
         this.selectedButton = null;
      }

   }

   protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
   }

   protected void actionPerformed(GuiButton button) throws IOException {
   }

   public void setWorldAndResolution(Minecraft mc, int width, int height) {
      this.mc = mc;
      this.itemRender = mc.getRenderItem();
      this.fontRendererObj = mc.fontRendererObj;
      this.width = width;
      this.height = height;
      this.buttonList.clear();
      this.initGui();
      this.mimeow$initGuiPost(mc, width, height, new CallbackInfo("setWorldAndResolution", false));
   }

   public void setGuiSize(int w, int h) {
      this.width = w;
      this.height = h;
   }

   public void initGui() {
   }

   public void handleInput() throws IOException {
      if(Mouse.isCreated()) {
         while(Mouse.next()) {
            this.handleMouseInput();
         }
      }

      if(Keyboard.isCreated()) {
         while(Keyboard.next()) {
            McpRuntime.EVENTS.post(new idle.miau.event.KeyEvent(Keyboard.getKeyName(Keyboard.getEventKey()),
                    Keyboard.getEventKey(), Keyboard.getEventKeyState(), true));
            this.handleKeyboardInput();
         }
      }

   }

   public void handleMouseInput() throws IOException {
      int i = Mouse.getEventX() * this.width / this.mc.displayWidth;
      int j = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
      int k = Mouse.getEventButton();
      if(Mouse.getEventButtonState()) {
         if(this.mc.gameSettings.touchscreen && this.touchValue++ > 0) {
            return;
         }

         this.eventButton = k;
         this.lastMouseEvent = Minecraft.getSystemTime();
         this.mouseClicked(i, j, this.eventButton);
      } else if(k != -1) {
         if(this.mc.gameSettings.touchscreen && --this.touchValue > 0) {
            return;
         }

         this.eventButton = -1;
         this.mouseReleased(i, j, k);
      } else if(this.eventButton != -1 && this.lastMouseEvent > 0L) {
         long l = Minecraft.getSystemTime() - this.lastMouseEvent;
         this.mouseClickMove(i, j, this.eventButton, l);
      }

   }

   public void handleKeyboardInput() throws IOException {
      CallbackInfo callbackInfo1 = new CallbackInfo("handleKeyboardInput", true);
      this.mimeow$injectHandleKeyboardInput(callbackInfo1);
      if(!callbackInfo1.isCancelled()) {
         if(Keyboard.getEventKeyState()) {
            this.keyTyped(Keyboard.getEventCharacter(), Keyboard.getEventKey());
         }

         this.mc.dispatchKeypresses();
      }
   }

   public void updateScreen() {
   }

   public void onGuiClosed() {
   }

   public void drawDefaultBackground() {
      this.drawWorldBackground(0);
   }

   public void drawWorldBackground(int tint) {
      if(this.mc.theWorld != null) {
         if (idle.miau.module.ModuleManager.patcher != null && idle.miau.module.ModuleManager.patcher.isEnabled()) {
            float bgOpacity = idle.miau.module.impl.other.Patcher.getContainerBackgroundOpacity();
            if (bgOpacity <= 0.001F) {
               GlStateManager.colorMask(false, false, false, false);
               drawRect(0, 0, this.width, this.height, -1);
               GlStateManager.colorMask(true, true, true, true);
               return;
            }
            int topAlpha = Math.max(0, (int)((bgOpacity - 16.0F / 255.0F) * 255.0F));
            int botAlpha = Math.max(0, (int)(bgOpacity * 255.0F));
            int startColor = (topAlpha << 24) | (-1072689136 & 0x00FFFFFF);
            int endColor = (botAlpha << 24) | (-804253680 & 0x00FFFFFF);
            this.drawGradientRect(0, 0, this.width, this.height, startColor, endColor);
            return;
         }
         this.drawGradientRect(0, 0, this.width, this.height, -1072689136, -804253680);
      } else {
         this.drawBackground(tint);
      }

   }

   public void drawBackground(int tint) {
      GlStateManager.disableLighting();
      GlStateManager.disableFog();
      Tessellator tessellator = Tessellator.getInstance();
      WorldRenderer worldrenderer = tessellator.getWorldRenderer();
      this.mc.getTextureManager().bindTexture(optionsBackground);
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      float f = 32.0F;
      worldrenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
      worldrenderer.pos(0.0D, (double)this.height, 0.0D).tex(0.0D, (double)((float)this.height / 32.0F + (float)tint)).color(64, 64, 64, 255).endVertex();
      worldrenderer.pos((double)this.width, (double)this.height, 0.0D).tex((double)((float)this.width / 32.0F), (double)((float)this.height / 32.0F + (float)tint)).color(64, 64, 64, 255).endVertex();
      worldrenderer.pos((double)this.width, 0.0D, 0.0D).tex((double)((float)this.width / 32.0F), (double)tint).color(64, 64, 64, 255).endVertex();
      worldrenderer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, (double)tint).color(64, 64, 64, 255).endVertex();
      tessellator.draw();
   }

   public boolean doesGuiPauseGame() {
      return true;
   }

   public void confirmClicked(boolean result, int id) {
      if(id == 31102009) {
         if(result) {
            this.openWebLink(this.clickedLinkURI);
         }

         this.clickedLinkURI = null;
         this.mc.displayGuiScreen(this);
      }

   }

   private void openWebLink(URI url) {
      try {
         Class<?> oclass = Class.forName("java.awt.Desktop");
         Object object = oclass.getMethod("getDesktop", new Class[0]).invoke((Object)null, new Object[0]);
         oclass.getMethod("browse", new Class[]{URI.class}).invoke(object, new Object[]{url});
      } catch (Throwable var4) {
         LOGGER.error("Couldn\'t open link", var4);
      }

   }

   public static boolean isCtrlKeyDown() {
      return Minecraft.isRunningOnMac?Keyboard.isKeyDown(219) || Keyboard.isKeyDown(220):Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
   }

   public static boolean isShiftKeyDown() {
      return Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
   }

   public static boolean isAltKeyDown() {
      return Keyboard.isKeyDown(56) || Keyboard.isKeyDown(184);
   }

   public static boolean isKeyComboCtrlX(int keyID) {
      return keyID == 45 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   public static boolean isKeyComboCtrlV(int keyID) {
      return keyID == 47 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   public static boolean isKeyComboCtrlC(int keyID) {
      return keyID == 46 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   public static boolean isKeyComboCtrlA(int keyID) {
      return keyID == 30 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   public void onResize(Minecraft mcIn, int w, int h) {
      this.setWorldAndResolution(mcIn, w, h);
   }

   private void mimeow$messageSend(String msg, boolean addToChat, CallbackInfo callbackInfo) {
      if(addToChat && MiauIRC.getInstance().handleChatInput(msg)) {
         this.mc.ingameGUI.getChatGUI().addToSentMessages(msg);
         callbackInfo.cancel();
         return;
      }
      if(addToChat && Miau.commandManager != null && Miau.commandManager.handleChatMessage(msg)) {
         this.mc.ingameGUI.getChatGUI().addToSentMessages(Miau.commandManager.redactForHistory(msg));
         callbackInfo.cancel();
      }

   }

   private void mimeow$injectHandleKeyboardInput(CallbackInfo callbackInfo) {
      KeyPressEvent event = new KeyPressEvent(Keyboard.getEventCharacter(), Keyboard.getEventKey());
      McpRuntime.EVENTS.post(event);
      if(event.isCanceled()) {
         callbackInfo.cancel();
      }

   }

   private void mimeow$initGuiPost(Minecraft mc, int width, int height, CallbackInfo ci) {
      McpRuntime.EVENTS.post(new Post(this, this.buttonList));
   }

   private void mimeow$redirect$actionPerformed(GuiScreen screen, GuiButton button) throws IOException {
      Pre pre = new Pre(screen, button, this.buttonList);
      if(!McpRuntime.EVENTS.post(pre)) {
         this.actionPerformed(button);
         McpRuntime.EVENTS.post(new idle.miau.mcp.event.client.GuiScreenEvent.ActionPerformedEvent.Post(screen, button, this.buttonList));
      }

   }

   public void callMouseClicked(int mouseX, int mouseY, int mouseButton) {
      try {
         this.mouseClicked(mouseX, mouseY, mouseButton);
      } catch (IOException exception) {
         throw new IllegalStateException(exception);
      }
   }
}
