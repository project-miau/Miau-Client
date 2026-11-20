package net.minecraft.client.gui;

import idle.miau.module.impl.render.HudStyle;
import idle.miau.module.impl.render.Chat;
import idle.miau.utility.font.MiauFontRenderer;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import idle.miau.Miau;
import idle.miau.clickgui.animation.ScrollOffsetAnimation;
import idle.miau.utility.RenderUtils;

public class GuiChat extends GuiScreen {
   private static final Logger logger = LogManager.getLogger();
   private String historyBuffer = "";
   private int sentHistoryCursor = -1;
   private boolean playerNamesFound;
   private boolean waitingOnAutocomplete;
   private int autocompleteIndex;
   private List<String> foundPlayerNames = Lists.newArrayList();
   protected GuiTextField inputField;
   private String defaultInputFieldText = "";
   private static final int MAX_PREVIEW_ROWS = 6;
   private static final float PREVIEW_SCROLL_SPEED = 20.0F;
   private String[] commandCompletions = new String[0];
   private int commandCompletionIndex = -1;
   private final ScrollOffsetAnimation previewScrollAnimation = new ScrollOffsetAnimation(200L);
   private final idle.miau.utility.Animation openAnimation = new idle.miau.utility.Animation(idle.miau.utility.Easing.EASE_OUT_CUBIC, 250L);
   private String previewScrollInput = "";

   public GuiChat() {
   }

   public GuiChat(String defaultText) {
      this.defaultInputFieldText = defaultText;
   }

   public void initGui() {
      Keyboard.enableRepeatEvents(true);
      this.sentHistoryCursor = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
      this.inputField = new GuiTextField(0, this.fontRendererObj, 4, this.height - 12, this.width - 4, 12);
      this.inputField.setMaxStringLength(idle.miau.module.impl.other.Patcher.isExtendedChatLengthActive() ? 256 : 100);
      this.inputField.setFontSupplier(() -> HudStyle.minecraftFont(this.fontRendererObj));
      this.inputField.setEnableBackgroundDrawing(false);
      this.inputField.setFocused(true);
      this.inputField.setText(this.defaultInputFieldText);
      this.inputField.setCanLoseFocus(false);
      this.openAnimation.reset();
      this.openAnimation.setValue(0.0);
      this.updateInputLength();
   }

   public void onGuiClosed() {
      Keyboard.enableRepeatEvents(false);
      this.mc.ingameGUI.getChatGUI().resetScroll();
      this.resetCommandCompletions();
      this.resetPreviewScroll();
   }

   public void updateScreen() {
      this.inputField.updateCursorCounter();
   }

   protected void keyTyped(char typedChar, int keyCode) throws IOException {
      if(keyCode != 15) {
         this.resetCommandCompletions();
      }
      this.waitingOnAutocomplete = false;
      if(keyCode == 15) {
         this.autocompletePlayerNames();
      } else {
         this.playerNamesFound = false;
      }

      if(keyCode == 1) {
         this.mc.displayGuiScreen((GuiScreen)null);
      } else if(keyCode != 28 && keyCode != 156) {
         if(keyCode == 200) {
            this.getSentHistory(-1);
         } else if(keyCode == 208) {
            this.getSentHistory(1);
         } else if(keyCode == 201) {
            this.mc.ingameGUI.getChatGUI().scroll(this.mc.ingameGUI.getChatGUI().getLineCount() - 1);
         } else if(keyCode == 209) {
            this.mc.ingameGUI.getChatGUI().scroll(-this.mc.ingameGUI.getChatGUI().getLineCount() + 1);
         } else {
            this.inputField.textboxKeyTyped(typedChar, keyCode);
         }
      } else {
         String s = this.inputField.getText().trim();
         if(s.length() > 0) {
            this.sendChatMessage(s);
         }

         if(this.mc.currentScreen == this) {
            this.mc.displayGuiScreen((GuiScreen)null);
         }
      }

      this.updateInputLength();
   }

   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      int i = Mouse.getEventDWheel();
      if(i != 0) {
         if(this.handleSuggestionScroll(i)) {
            return;
         }
         if(i > 1) {
            i = 1;
         }

         if(i < -1) {
            i = -1;
         }

         if(!isShiftKeyDown()) {
            i *= 7;
         }

         this.mc.ingameGUI.getChatGUI().scroll(i);
      }
   }

   protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if(mouseButton == 0) {
         IChatComponent ichatcomponent = this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());
         if(this.handleComponentClick(ichatcomponent)) {
            return;
         }
      }

      this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
      super.mouseClicked(mouseX, mouseY, mouseButton);
   }

   protected void setText(String newChatText, boolean shouldOverwrite) {
      if(shouldOverwrite) {
         this.inputField.setText(newChatText);
      } else {
         this.inputField.writeText(newChatText);
      }

      this.resetCommandCompletions();
      this.updateInputLength();
   }

   public void autocompletePlayerNames() {
      if(this.handleCommandAutocomplete()) {
         return;
      }
      if(this.playerNamesFound) {
         this.inputField.deleteFromCursor(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());
         if(this.autocompleteIndex >= this.foundPlayerNames.size()) {
            this.autocompleteIndex = 0;
         }
      } else {
         int i = this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false);
         this.foundPlayerNames.clear();
         this.autocompleteIndex = 0;
         String s = this.inputField.getText().substring(i).toLowerCase();
         String s1 = this.inputField.getText().substring(0, this.inputField.getCursorPosition());
         this.sendAutocompleteRequest(s1, s);
         if(this.foundPlayerNames.isEmpty()) {
            return;
         }

         this.playerNamesFound = true;
         this.inputField.deleteFromCursor(i - this.inputField.getCursorPosition());
      }

      if(this.foundPlayerNames.size() > 1) {
         StringBuilder stringbuilder = new StringBuilder();

         for(String s2 : this.foundPlayerNames) {
            if(stringbuilder.length() > 0) {
               stringbuilder.append(", ");
            }

            stringbuilder.append(s2);
         }

         this.mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(new ChatComponentText(stringbuilder.toString()), 1);
      }

      this.inputField.writeText((String)this.foundPlayerNames.get(this.autocompleteIndex++));
   }

   private void sendAutocompleteRequest(String p_146405_1_, String p_146405_2_) {
      if(Miau.commandManager != null && Miau.commandManager.isCommand(p_146405_1_)) {
         return;
      }
      if(idle.miau.baritone.BaritoneBridge.isCommand(p_146405_1_)) return;
      if(p_146405_1_.length() >= 1) {
         BlockPos blockpos = null;
         if(this.mc.objectMouseOver != null && this.mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
            blockpos = this.mc.objectMouseOver.getBlockPos();
         }

         this.mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(p_146405_1_, blockpos));
         this.waitingOnAutocomplete = true;
      }
   }

   public void getSentHistory(int msgPos) {
      int i = this.sentHistoryCursor + msgPos;
      int j = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
      i = MathHelper.clamp_int(i, 0, j);
      if(i != this.sentHistoryCursor) {
         if(i == j) {
            this.sentHistoryCursor = j;
            this.inputField.setText(this.historyBuffer);
         } else {
            if(this.sentHistoryCursor == j) {
               this.historyBuffer = this.inputField.getText();
            }

            this.inputField.setText((String)this.mc.ingameGUI.getChatGUI().getSentMessages().get(i));
            this.sentHistoryCursor = i;
         }
      }

   }

   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      boolean chatActive = idle.miau.module.impl.render.Chat.isActive();
      boolean openAnim = chatActive && idle.miau.module.impl.render.Chat.isOpenAnimationActive();
      float animScale = 1.0F;

      if (openAnim) {
         this.openAnimation.run(1.0);
         animScale = (float) this.openAnimation.getValue();
         animScale = MathHelper.clamp_float(animScale, 0.0F, 1.0F);
      }

      float boxLeft = 2.0F;
      MiauFontRenderer inputFont = HudStyle.minecraftFont(this.fontRendererObj);
      float inputPadding = Math.max(2, HudStyle.paddingY());
      float boxTop = inputPanelTop();
      this.inputField.xPosition = 4 + Math.round(HudStyle.paddingX());
      this.inputField.yPosition = Math.round(boxTop + inputPadding);
      this.inputField.setSize(this.width - this.inputField.xPosition - 4, Math.round(this.height - 2 - boxTop));
      float boxRight = (float) (this.width - 2);
      float boxBottom = (float) (this.height - 2);

      if (openAnim && animScale < 1.0F) {
         GlStateManager.pushMatrix();
         float cx = (boxLeft + boxRight) / 2.0F;
         float cy = (boxTop + boxBottom) / 2.0F;
         GlStateManager.translate(cx * (1.0F - animScale), cy * (1.0F - animScale), 0.0F);
         GlStateManager.scale(animScale, animScale, 1.0F);
      }

      HudStyle.panel(new HudStyle.Panel(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop, () -> {
         if (this.inputField.getText().isEmpty()) {
            inputFont.drawString("# + Message to chat IRC", this.inputField.xPosition,
                    this.inputField.yPosition, 0x50D0D0D0, false);
         }
         this.inputField.drawTextBox();
      }), Chat.inputPanelStyle());

      if (openAnim && animScale < 1.0F) {
         GlStateManager.popMatrix();
      }

      IChatComponent ichatcomponent = this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());
      if (ichatcomponent != null && ichatcomponent.getChatStyle().getChatHoverEvent() != null) {
         this.handleComponentHover(ichatcomponent, mouseX, mouseY);
      }

      super.drawScreen(mouseX, mouseY, partialTicks);
      this.drawCommandSuggestions();
   }

   public void onAutocompleteResponse(String[] p_146406_1_) {
      if(this.waitingOnAutocomplete) {
         this.playerNamesFound = false;
         this.foundPlayerNames.clear();

         for(String s : p_146406_1_) {
            if(s.length() > 0) {
               this.foundPlayerNames.add(s);
            }
         }

         String s1 = this.inputField.getText().substring(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false));
         String s2 = StringUtils.getCommonPrefix(p_146406_1_);
         if(s2.length() > 0 && !s1.equalsIgnoreCase(s2)) {
            this.inputField.deleteFromCursor(this.inputField.func_146197_a(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());
            this.inputField.writeText(s2);
         } else if(this.foundPlayerNames.size() > 0) {
            this.playerNamesFound = true;
            this.autocompletePlayerNames();
         }
      }

   }

   public boolean doesGuiPauseGame() {
      return false;
   }

   private void updateInputLength() {
      int maxLength = idle.miau.baritone.BaritoneBridge.isCommand(this.inputField.getText())
              || Miau.commandManager != null && Miau.commandManager.isCommand(this.inputField.getText()) ? 256 : 100;
      this.inputField.setMaxStringLength(maxLength);
   }

   private boolean handleCommandAutocomplete() {
      if(Miau.commandManager == null) {
         return false;
      }
      String input = this.inputField.getText();
      boolean baritoneCommand = idle.miau.baritone.BaritoneBridge.isCommand(input);
      if(!baritoneCommand && !Miau.commandManager.isCommand(input)) {
         this.resetCommandCompletions();
         return false;
      }

      String[] suggestions;
      if(this.canCycleCurrentCompletion(input)) {
         suggestions = this.commandCompletions;
         this.commandCompletionIndex = (this.commandCompletionIndex + 1) % suggestions.length;
      } else {
         suggestions = baritoneCommand ? idle.miau.baritone.BaritoneBridge.autocomplete(input)
                 : Miau.commandManager.getAutoComplete(input);
         if(suggestions.length == 0) {
            this.resetCommandCompletions();
            return true;
         }
         this.commandCompletions = suggestions;
         this.commandCompletionIndex = 0;
      }

      this.inputField.setText(suggestions[this.commandCompletionIndex]);
      this.inputField.setCursorPositionEnd();
      this.updateInputLength();
      return true;
   }

   private boolean canCycleCurrentCompletion(String input) {
      if(this.commandCompletions.length == 0 || this.commandCompletionIndex < 0) {
         return false;
      }
      for(String suggestion : this.commandCompletions) {
         if(suggestion.equals(input)) {
            return true;
         }
      }
      return false;
   }

   private void resetCommandCompletions() {
      this.commandCompletions = new String[0];
      this.commandCompletionIndex = -1;
   }

   private boolean handleSuggestionScroll(int wheelInput) {
      if(Miau.commandManager == null) {
         return false;
      }
      String input = this.inputField.getText();
      if(!Miau.commandManager.isCommand(input)) {
         this.resetPreviewScroll();
         return false;
      }

      String[] suggestions = Miau.commandManager.getPreviewSuggestions(input);
      int visibleRows = Math.min(MAX_PREVIEW_ROWS, suggestions.length);
      this.syncPreviewScrollState(input, suggestions.length, visibleRows);
      if(visibleRows == 0 || suggestions.length <= visibleRows) {
         return false;
      }

      int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
      int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
      if(!this.isMouseOverPreviewPanel(mouseX, mouseY, suggestions, visibleRows)) {
         return false;
      }

      float delta = PREVIEW_SCROLL_SPEED * ((float)wheelInput / 120.0F);
      if(delta != 0.0F) {
         this.previewScrollAnimation.extend(-delta);
         this.clampPreviewScroll(suggestions.length, visibleRows);
      }
      return true;
   }

   private void drawCommandSuggestions() {
      if(Miau.commandManager == null) {
         this.resetPreviewScroll();
         return;
      }
      String input = this.inputField.getText();
      if(!Miau.commandManager.isCommand(input)) {
         this.resetPreviewScroll();
         return;
      }

      String[] suggestions = Miau.commandManager.getPreviewSuggestions(input);
      if(suggestions.length == 0) {
         this.resetPreviewScroll();
         return;
      }

      int rows = Math.min(MAX_PREVIEW_ROWS, suggestions.length);
      this.syncPreviewScrollState(input, suggestions.length, rows);
      MiauFontRenderer font = HudStyle.minecraftFont(this.fontRendererObj);
      int fontHeight = font.getFontHeight();
      int rowHeight = previewRowHeight();
      int widest = 0;
      for(String suggestion : suggestions) {
         if(suggestion != null) widest = Math.max(widest, font.getStringWidth(suggestion));
      }
      int panelLeft = 4;
      int panelRight = Math.min(this.width - 4, panelLeft + widest + 8);
      int panelBottom = Math.round(inputPanelTop()) - 2;
      int panelTop = panelBottom - rows * rowHeight - 2;
      int contentTop = panelTop + 1;
      int contentHeight = rows * rowHeight;
      float scrollOffset = this.previewScrollAnimation.getValue();
      float alpha = this.mc.gameSettings.chatOpacity * .9f + .1f;
      HudStyle.panel(new HudStyle.Panel(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, alpha, () -> {
         RenderUtils.scissorPushGui(panelLeft, contentTop, panelRight - panelLeft, contentHeight);
         try {
            for(int i = 0; i < suggestions.length; ++i) {
               String suggestion = suggestions[i];
               int textY = Math.round(contentTop - scrollOffset + i * rowHeight);
               if(suggestion != null && textY + fontHeight >= contentTop && textY <= contentTop + contentHeight) {
                  font.drawString(suggestion, panelLeft + 4, textY, 0xFFFFFFFF, false);
               }
            }
         } finally {
            RenderUtils.scissorPop();
         }
      }, null), Chat.panelStyle());
   }

   private float inputPanelTop() {
      return this.height - 2 - Math.max(12, HudStyle.minecraftFont(this.fontRendererObj).getFontHeight() + Math.max(2, HudStyle.paddingY()) * 2);
   }

   private int previewRowHeight() {
      return HudStyle.minecraftFont(this.fontRendererObj).getFontHeight() + Math.max(2, Math.round(HudStyle.paddingY() * 2));
   }

   private void syncPreviewScrollState(String input, int suggestionCount, int visibleRows) {
      if(!input.equals(this.previewScrollInput)) {
         this.previewScrollInput = input;
         this.previewScrollAnimation.reset(0.0F);
      }
      this.clampPreviewScroll(suggestionCount, visibleRows);
   }

   private void clampPreviewScroll(int suggestionCount, int visibleRows) {
      int rowHeight = previewRowHeight();
      float maxScroll = Math.max(0.0F, (suggestionCount - visibleRows) * rowHeight);
      this.previewScrollAnimation.clampTarget(0.0F, maxScroll);
   }

   private boolean isMouseOverPreviewPanel(int mouseX, int mouseY, String[] suggestions, int visibleRows) {
      int widest = 0;
      for(String suggestion : suggestions) {
         if(suggestion != null) {
            widest = Math.max(widest, HudStyle.minecraftFont(this.fontRendererObj).getStringWidth(suggestion));
         }
      }
      int panelLeft = 4;
      int panelRight = Math.min(this.width - 4, panelLeft + widest + 8);
      int panelBottom = Math.round(inputPanelTop()) - 2;
      int panelTop = panelBottom - visibleRows * previewRowHeight() - 2;
      return mouseX >= panelLeft && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom;
   }

   private void resetPreviewScroll() {
      this.previewScrollInput = "";
      this.previewScrollAnimation.reset(0.0F);
   }

}
