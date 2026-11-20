package dev.idle.igam.gui;

import java.util.List;
import dev.idle.igam.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import org.lwjgl.input.Keyboard;

public class GuiImportResult extends GuiScreen {
  private final GuiScreen previousScreen;
  private final List<String> usernames;
  private final int failedCount;

  private UsernameList usernameList;

  public GuiImportResult(GuiScreen previousScreen, List<String> usernames, int failedCount) {
    this.previousScreen = previousScreen;
    this.usernames = usernames;
    this.failedCount = failedCount;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    Keyboard.enableRepeatEvents(true);

    usernameList = new UsernameList(mc);
    usernameList.registerScrollButtons(11, 12);

    buttonList.add(new GuiButton(0, width / 2 - 100, height - 30, 200, 20, "Back"));
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);
  }

  @Override
  public void handleMouseInput() throws java.io.IOException {
    if (usernameList != null) {
      usernameList.handleMouseInput();
    }
    super.handleMouseInput();
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button.enabled && button.id == 0) {
      mc.displayGuiScreen(previousScreen);
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      mc.displayGuiScreen(previousScreen);
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    usernameList.drawScreen(mouseX, mouseY, partialTicks);
    drawCenteredString(fontRendererObj, TextFormatting.translate("&a&lImport Completed&r"), width / 2, 10, -1);

    String infoStr = String.format("&7Successfully imported: &a%d&r &8| &7Failed: &c%d&r", usernames.size(), failedCount);
    drawCenteredString(fontRendererObj, TextFormatting.translate(infoStr), width / 2, 25, -1);

    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  private class UsernameList extends GuiSlot {
    public UsernameList(Minecraft mc) {
      super(mc, GuiImportResult.this.width, GuiImportResult.this.height, 40, GuiImportResult.this.height - 40, 18);
    }

    @Override
    protected int getSize() {
      return usernames.size();
    }

    @Override
    protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
      if (isDoubleClick && slotIndex >= 0 && slotIndex < usernames.size()) {
        GuiScreen.setClipboardString(usernames.get(slotIndex));
      }
    }

    @Override
    protected boolean isSelected(int slotIndex) {
      return false;
    }

    @Override
    protected void drawBackground() {
      GuiImportResult.this.drawDefaultBackground();
    }

    @Override
    public int getListWidth() {
      return Math.min(width - 40, 300);
    }

    @Override
    protected int getScrollBarX() {
      return (this.width + getListWidth()) / 2 + 2;
    }

    @Override
    protected void drawSlot(int entryID, int p_180791_2_, int p_180791_3_, int p_180791_4_, int mouseXIn, int mouseYIn) {
      if (entryID >= usernames.size()) return;
      String username = usernames.get(entryID);
      GuiImportResult.this.drawString(GuiImportResult.this.fontRendererObj, 
          TextFormatting.translate(String.format("&8%d. &b%s&r", entryID + 1, username)), 
          p_180791_2_ + 2, p_180791_3_ + 2, -1);
    }
  }
}
