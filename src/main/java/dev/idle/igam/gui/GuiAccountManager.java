package dev.idle.igam.gui;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import dev.idle.igam.AccountManager;
import dev.idle.igam.auth.Account;
import dev.idle.igam.auth.CrackedAccountGenerator;
import dev.idle.igam.auth.MinecraftMicrosoftAuth;
import dev.idle.igam.auth.MicrosoftAuth;
import dev.idle.igam.auth.SessionManager;
import dev.idle.igam.utils.Notification;
import dev.idle.igam.utils.TextFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.input.Keyboard;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiAccountManager extends GuiScreen {
  private static final int LABEL_COLOR = 0xFFAAAAAA;
  private static final int VALUE_COLOR = 0xFFFFFFFF;
  private static final int SUCCESS_COLOR = 0xFF55FF55;
  private static final int ERROR_COLOR = 0xFFFF5555;
  private final GuiScreen previousScreen;

  private GuiButton loginButton = null;
  private GuiButton deleteButton = null;
  private GuiButton cancelButton = null;
  private GuiButton restoreButton = null;
  private GuiButton randomCrackedButton = null;
  private GuiAccountList guiAccountList = null;
  private Notification notification = null;
  private int selectedAccount = -1;
  private ExecutorService executor = null;
  private CompletableFuture<Void> task = null;
  private String taskMessage = "&eWorking...";
  private int controlsLeft;
  private int controlsWidth;
  private int detailsLeft;
  private int detailsRight;
  private int accountListLeft;
  private int accountListWidth;
  private int contentTop;
  private int contentBottom;
  private int accountNameCopyLeft = -1;
  private int accountNameCopyRight = -1;
  private int accountNameCopyTop = -1;
  private int accountNameCopyBottom = -1;
  private int uidCopyLeft = -1;
  private int uidCopyRight = -1;
  private int uidCopyTop = -1;
  private int uidCopyBottom = -1;

  public GuiAccountManager(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  public GuiAccountManager(GuiScreen previousScreen, Notification notification) {
    this.previousScreen = previousScreen;
    this.notification = notification;
  }

  @Override
  public void initGui() {
    AccountManager.load();
    Keyboard.enableRepeatEvents(true);
    buttonList.clear();

    int margin = 8;
    int gap = 8;
    controlsWidth = Math.max(110, Math.min(150, width / 5));
    accountListWidth = Math.max(120, Math.min(300, width / 3));
    controlsLeft = margin;
    accountListLeft = width - margin - accountListWidth;
    detailsLeft = controlsLeft + controlsWidth + gap;
    detailsRight = accountListLeft - gap;
    contentTop = 45;
    contentBottom = height - 8;

    int buttonX = controlsLeft + 4;
    int buttonWidth = controlsWidth - 8;
    int buttonY = contentTop + 8;
    int buttonCount = 9;
    int buttonGap = 2;
    int availableButtonHeight = contentBottom - buttonY;
    int buttonHeight = Math.min(20, Math.max(12,
        (availableButtonHeight - buttonGap * (buttonCount - 1)) / buttonCount));
    int buttonStep = buttonHeight + buttonGap;
    buttonList.add(loginButton = new GuiButton(0, buttonX, buttonY, buttonWidth, buttonHeight, "Login"));
    buttonList.add(deleteButton = new GuiButton(2, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "Delete"));
    buttonList.add(new GuiButton(1, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "Microsoft"));
    buttonList.add(new GuiButton(5, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "Token / Session"));
    buttonList.add(new GuiButton(6, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "Cracked"));
    buttonList.add(randomCrackedButton = new GuiButton(
        9, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "Random Cracked"));
    buttonList.add(new GuiButton(7, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "Cookie"));
    buttonList.add(new GuiButton(8, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "LocalTS"));
    buttonList.add(cancelButton = new GuiButton(3, buttonX, buttonY += buttonStep, buttonWidth, buttonHeight, "Cancel"));

    if (SessionManager.getLaunchSession() != null) {
      String launchName = SessionManager.getLaunchSession().getUsername();
      String label = "Restore: " + launchName;
      int restoreWidth = Math.min(200, fontRendererObj.getStringWidth(label) + 12);
      restoreButton = new GuiButton(4, width - restoreWidth - 8, 8, restoreWidth, 20,
          fontRendererObj.trimStringToWidth(label, restoreWidth - 10));
      buttonList.add(restoreButton);
    } else {
      restoreButton = null;
    }

    guiAccountList = new GuiAccountList(mc);
    guiAccountList.registerScrollButtons(11, 12);
    updateScreen();
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);

    if (task != null && !task.isDone()) {
      task.cancel(true);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public void updateScreen() {
    if (loginButton != null && deleteButton != null) {
      loginButton.enabled = deleteButton.enabled = selectedAccount >= 0;
      if (task != null && !task.isDone()) {
        loginButton.enabled = false;
      }
    }
    if (restoreButton != null) {
      restoreButton.enabled = !SessionManager.isUsingLaunchSession()
          && (task == null || task.isDone());
    }
    if (randomCrackedButton != null) {
      randomCrackedButton.enabled = task == null || task.isDone();
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float renderPartialTicks) {
    if (guiAccountList != null) {
      guiAccountList.drawScreen(mouseX, mouseY, renderPartialTicks);
    }
    Gui.drawRect(controlsLeft, contentTop, controlsLeft + controlsWidth, contentBottom, 0x76000000);
    Gui.drawRect(detailsLeft, contentTop, detailsRight, contentBottom, 0x52000000);
    super.drawScreen(mouseX, mouseY, renderPartialTicks);

    int titleCenter = width / 2;
    drawCenteredString(
        fontRendererObj,
        TextFormatting.translate("&cAccount Manager"),
        titleCenter,
        14,
        -1);
    String status = "&aWaiting...";
    if (task != null && !task.isDone()) {
      status = taskMessage;
    } else if (notification != null && !notification.isExpired()) {
      status = notification.getMessage();
    }
    drawCenteredString(fontRendererObj, TextFormatting.translate(status), titleCenter, 28, -1);

    String text =
        TextFormatting.translate(
            String.format("&7Username: &f%s &8[&7%s&8]&r",
                SessionManager.get().getUsername(), AccountManager.accounts.size()));
    mc.currentScreen.drawString(mc.fontRendererObj, text, 3, 3, -1);

    int detailCenter = detailsLeft + Math.max(0, detailsRight - detailsLeft) / 2;
    int detailY = contentTop + 12;
    accountNameCopyLeft = accountNameCopyRight = accountNameCopyTop = accountNameCopyBottom = -1;
    uidCopyLeft = uidCopyRight = uidCopyTop = uidCopyBottom = -1;
    drawCenteredString(fontRendererObj, "Detailed information:", detailCenter, detailY, 0xFFFFFFFF);
    if (selectedAccount >= 0 && selectedAccount < AccountManager.accounts.size()) {
      Account selected = AccountManager.accounts.get(selectedAccount);
      int accountNameY = detailY + 14;
      int[] accountNameBounds = drawCenteredLabelValue(
          "Account: ", displayUsername(selected), detailCenter, accountNameY, LABEL_COLOR, VALUE_COLOR);
      accountNameCopyLeft = accountNameBounds[0];
      accountNameCopyRight = accountNameBounds[1];
      accountNameCopyTop = accountNameY;
      accountNameCopyBottom = accountNameY + 9;

      int uidY = detailY + 26;
      int[] uidBounds = drawCenteredLabelValue(
          "UID: ", displayUid(selected), detailCenter, uidY, LABEL_COLOR, VALUE_COLOR);
      uidCopyLeft = uidBounds[0];
      uidCopyRight = uidBounds[1];
      uidCopyTop = uidY;
      uidCopyBottom = uidY + 9;

      drawCenteredLabelValue(
          "Type: ", displayType(selected), detailCenter, detailY + 38, LABEL_COLOR, VALUE_COLOR);
      drawCenteredLabelValue(
          "Last login: ", displayLastLogin(selected), detailCenter, detailY + 50, LABEL_COLOR, VALUE_COLOR);
      drawCenteredLabelValue(
          "Play time: ", displayPlayTime(selected.getPlayTime()), detailCenter, detailY + 62, LABEL_COLOR, VALUE_COLOR);
      drawCenteredLabelValue(
          "Hypixel State: ", displayHypixelState(selected), detailCenter, detailY + 74,
          LABEL_COLOR, displayHypixelStateColor(selected));
      if (isCurrentAccount(selected)) {
        drawCenteredString(fontRendererObj, TextFormatting.translate("&aCurrent session"), detailCenter, detailY + 90, 0xFFFFFFFF);
      }
    } else {
      drawCenteredString(fontRendererObj, TextFormatting.translate("&7Select an account"), detailCenter, detailY + 20, 0xFFFFFFFF);
    }
  }

  private String displayUsername(Account account) {
    return StringUtils.isBlank(account.getUsername()) ? "?" : account.getUsername();
  }

  private String displayType(Account account) {
    switch (account.getType()) {
      case Account.TYPE_CRACKED:
        return "Cracked";
      case Account.TYPE_COOKIE:
        return "Cookie";
      case Account.TYPE_TOKEN:
        return "Token";
      case Account.TYPE_SESSION:
        return "Session";
      case Account.TYPE_REFRESH:
        return "Refresh Token";
      default:
        return "Microsoft";
    }
  }

  private String displayUid(Account account) {
    return StringUtils.isBlank(account.getUid()) ? "Unknown" : account.getUid();
  }

  private String displayLastLogin(Account account) {
    long lastLogin = account.getLastLogin();
    if (lastLogin <= 0L) return "Never";
    ZoneId zone = ZoneId.systemDefault();
    LocalDate loginDate = Instant.ofEpochMilli(lastLogin).atZone(zone).toLocalDate();
    if (LocalDate.now(zone).equals(loginDate)) return "Today";
    return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .format(Instant.ofEpochMilli(lastLogin).atZone(zone));
  }

  private String displayPlayTime(long milliseconds) {
    long totalMinutes = Math.max(0L, milliseconds) / 60000L;
    long days = totalMinutes / 1440L;
    long hours = totalMinutes % 1440L / 60L;
    long minutes = totalMinutes % 60L;
    if (days > 0L) return days + "d " + hours + "h " + minutes + "m";
    if (hours > 0L) return hours + "h " + minutes + "m";
    return minutes + "m";
  }

  private String displayHypixelState(Account account) {
    long unban = account.getUnban();
    if (unban < 0L) return "Banned permanently";
    long remaining = unban - System.currentTimeMillis();
    if (remaining > 0L) return "Banned - " + displayRemainingTime(remaining);
    return "Unbanned";
  }

  private int displayHypixelStateColor(Account account) {
    return account.getUnban() < 0L || account.getUnban() > System.currentTimeMillis()
        ? ERROR_COLOR : SUCCESS_COLOR;
  }

  private String displayRemainingTime(long milliseconds) {
    long totalSeconds = Math.max(1L, milliseconds / 1000L);
    long days = totalSeconds / 86400L;
    long hours = totalSeconds % 86400L / 3600L;
    long minutes = totalSeconds % 3600L / 60L;
    if (days > 0L) return days + "d " + hours + "h";
    if (hours > 0L) return hours + "h " + minutes + "m";
    return Math.max(1L, minutes) + "m";
  }

  private boolean isInside(int mouseX, int mouseY, int left, int top, int right, int bottom) {
    return mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom;
  }

  private int[] drawCenteredLabelValue(
      String label, String value, int centerX, int y, int labelColor, int valueColor) {
    int labelWidth = fontRendererObj.getStringWidth(label);
    int valueWidth = fontRendererObj.getStringWidth(value);
    int left = centerX - (labelWidth + valueWidth) / 2;
    drawString(fontRendererObj, label, left, y, labelColor);
    drawString(fontRendererObj, value, left + labelWidth, y, valueColor);
    return new int[]{left, left + labelWidth + valueWidth};
  }

  private void drawLabelValue(
      FontRenderer renderer, String label, String value, int x, int y, int maximumWidth) {
    int labelWidth = renderer.getStringWidth(label);
    String visibleValue = renderer.trimStringToWidth(value, Math.max(0, maximumWidth - labelWidth));
    drawString(renderer, label, x, y, LABEL_COLOR);
    drawString(renderer, visibleValue, x + labelWidth, y, VALUE_COLOR);
  }

  private boolean isCurrentAccount(Account account) {
    String accessToken = account.getAccessToken();
    if (!StringUtils.isBlank(accessToken) && accessToken.equals(SessionManager.get().getToken())) return true;
    return displayUsername(account).equals(SessionManager.get().getUsername());
  }

  private void loginCrackedAccount(Account account, String message) {
    SessionManager.set(SessionManager.offline(account.getUsername()));
    notification =
        new Notification(
            TextFormatting.translate(String.format(message, account.getUsername())),
            5000L);
  }

  private void createAndLoginRandomCrackedAccount() {
    if (task != null && !task.isDone()) {
      return;
    }

    ensureExecutor();
    taskMessage = "&eGenerating cracked account...";
    notification = new Notification(TextFormatting.translate(taskMessage), -1L);
    ArrayList<Account> accounts = new ArrayList<>(AccountManager.accounts);
    task = CompletableFuture
        .supplyAsync(
            () -> {
              try {
                return CrackedAccountGenerator.nextUsername(accounts);
              } catch (IOException exception) {
                throw new CompletionException(exception);
              }
            },
            executor)
        .handle(
            (username, error) -> {
              mc.addScheduledTask(
                  () -> {
                    if (mc.currentScreen != this) {
                      return;
                    }
                    if (error != null) {
                      notification = new Notification(
                          TextFormatting.translate("&cUnable to fetch a random username.&r"),
                          5000L);
                    } else {
                      Account stored = AccountManager.store(Account.cracked(username));
                      selectedAccount = AccountManager.accounts.indexOf(stored);
                      loginCrackedAccount(stored, "&aCreated and logged in! (%s)&r");
                    }
                    updateScreen();
                  });
              return null;
            });
    updateScreen();
  }

  private void ensureExecutor() {
    if (executor == null || executor.isShutdown()) {
      executor = Executors.newSingleThreadExecutor(
          runnable -> {
            Thread thread = new Thread(runnable, "Mimeow-Account-Login");
            thread.setDaemon(true);
            return thread;
          });
    }
  }

  private void loginAccount(Account account) {
    String username = StringUtils.isBlank(account.getUsername()) ? "???" : account.getUsername();
    taskMessage = "&eLogging in...";
    notification = new Notification(
        TextFormatting.translate(
            String.format("&7Fetching your Minecraft profile... (%s)&r", username)),
        -1L);
    ensureExecutor();

    if (!account.getAuthData().isEmpty() || !account.getRefreshToken().isEmpty()) {
      task = CompletableFuture
          .supplyAsync(() -> {
            try {
              if (!account.getAuthData().isEmpty()) {
                return MinecraftMicrosoftAuth.restore(account.getAuthData());
              }
              return MinecraftMicrosoftAuth.migrate(account.getRefreshToken());
            } catch (Exception error) {
              throw new CompletionException(error);
            }
          }, executor)
          .handle((result, error) -> {
            mc.addScheduledTask(() -> finishMicrosoftLogin(account, username, result, error));
            return null;
          });
    } else {
      task = MicrosoftAuth.login(account.getAccessToken(), executor)
          .handle((session, error) -> {
            mc.addScheduledTask(() -> finishTokenLogin(account, username, session, error));
            return null;
          });
    }
    updateScreen();
  }

  private void finishMicrosoftLogin(
      Account account,
      String username,
      MinecraftMicrosoftAuth.LoginResult result,
      Throwable error) {
    if (mc.currentScreen != this) return;
    if (error != null) {
      showLoginError(username, MinecraftMicrosoftAuth.describe(error));
      return;
    }
    result.applyTo(account);
    AccountManager.save();
    finishLogin(result.getSession());
  }

  private void finishTokenLogin(
      Account account,
      String username,
      net.minecraft.util.Session session,
      Throwable error) {
    if (mc.currentScreen != this) return;
    if (error != null || session == null) {
      showLoginError(username, MinecraftMicrosoftAuth.describe(error));
      return;
    }
    account.setUsername(session.getUsername());
    account.setUid(session.getPlayerID());
    finishLogin(session);
  }

  private void finishLogin(net.minecraft.util.Session session) {
    SessionManager.set(session);
    notification = new Notification(
        TextFormatting.translate(
            String.format("&aSuccessful login! (%s)&r", session.getUsername())),
        5000L);
    updateScreen();
  }

  private void showLoginError(String username, String message) {
    notification = new Notification(
        TextFormatting.translate(String.format("&c%s (%s)&r", message, username)),
        5000L);
    updateScreen();
  }

  void setNotification(Notification notification) {
    this.notification = notification;
  }

  @Override
  public void handleMouseInput() throws IOException {
    if (guiAccountList != null) {
      guiAccountList.handleMouseInput();
    }
    super.handleMouseInput();
  }

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
    super.mouseClicked(mouseX, mouseY, mouseButton);
    if (mouseButton != 0) return;
    if (isInside(mouseX, mouseY, accountNameCopyLeft, accountNameCopyTop,
        accountNameCopyRight, accountNameCopyBottom)
        && selectedAccount >= 0 && selectedAccount < AccountManager.accounts.size()) {
      setClipboardString(displayUsername(AccountManager.accounts.get(selectedAccount)));
      notification = new Notification(TextFormatting.translate("&aAccount name copied"), 2000L);
      return;
    }
    if (isInside(mouseX, mouseY, uidCopyLeft, uidCopyTop, uidCopyRight, uidCopyBottom)
        && selectedAccount >= 0 && selectedAccount < AccountManager.accounts.size()) {
      String uid = AccountManager.accounts.get(selectedAccount).getUid();
      if (!StringUtils.isBlank(uid)) {
        setClipboardString(uid);
        notification = new Notification(TextFormatting.translate("&aUID copied"), 2000L);
      }
      return;
    }
    if (guiAccountList != null) {
      int slot = guiAccountList.getSlotIndexFromScreenCoords(mouseX, mouseY);
      if (slot >= 0 && slot < AccountManager.accounts.size()) {
        selectedAccount = slot;
        updateScreen();
      }
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    switch (keyCode) {
      case Keyboard.KEY_UP:
        {
          if (selectedAccount > 0) {
            --selectedAccount;
            if (isCtrlKeyDown()) {
              Collections.swap(AccountManager.accounts, selectedAccount, selectedAccount + 1);
              AccountManager.save();
            }
          }
        }
        break;
      case Keyboard.KEY_DOWN:
        {
          if (selectedAccount < AccountManager.accounts.size() - 1) {
            ++selectedAccount;
            if (isCtrlKeyDown()) {
              Collections.swap(AccountManager.accounts, selectedAccount, selectedAccount - 1);
              AccountManager.save();
            }
          }
        }
        break;
      case Keyboard.KEY_RETURN:
        {
          actionPerformed(loginButton);
        }
        break;
      case Keyboard.KEY_DELETE:
        {
          actionPerformed(deleteButton);
        }
        break;
      case Keyboard.KEY_ESCAPE:
        {
          actionPerformed(cancelButton);
        }
        break;
    }

    if (isKeyComboCtrlC(keyCode) && selectedAccount >= 0) {
      setClipboardString(AccountManager.accounts.get(selectedAccount).getUsername());
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button == null) {
      return;
    }

    if (button.enabled) {
      switch (button.id) {
        case 0:
          {
            if (task == null || task.isDone()) {
              Account account = AccountManager.accounts.get(selectedAccount);

              if (account.isCracked()) {
                loginCrackedAccount(account, "&aSuccessful login! (%s)&r");
                break;
              }
              loginAccount(account);
            }
          }
          break;
        case 1:
          {
            mc.displayGuiScreen(new GuiMicrosoftDeviceCode(this));
          }
          break;
        case 2:
          {
            if (selectedAccount > -1 && selectedAccount < AccountManager.accounts.size()) {
              AccountManager.accounts.remove(selectedAccount);
              AccountManager.save();
              selectedAccount = -1;
              updateScreen();
            }
          }
          break;
        case 3:
          {
            mc.displayGuiScreen(previousScreen);
          }
          break;
        case 5:
          {
            mc.displayGuiScreen(new GuiAddToken(this));
          }
          break;
        case 6:
          {
            mc.displayGuiScreen(new GuiCrackedLogin(this));
          }
          break;
        case 7:
          {
            mc.displayGuiScreen(new GuiCookieLogin(this));
          }
          break;
        case 8:
          {
            mc.displayGuiScreen(new GuiLocalTS(this));
          }
          break;
        case 4:
          {
            if (SessionManager.restoreLaunchSession()) {
              notification = new Notification(
                  TextFormatting.translate(
                      String.format("&aRestored launch session! (%s)&r",
                          SessionManager.get().getUsername())),
                  5000L);
              updateScreen();
            }
          }
          break;
        case 9:
          {
            createAndLoginRandomCrackedAccount();
          }
          break;
        default:
          {
            guiAccountList.actionPerformed(button);
          }
      }
    }
  }

  class GuiAccountList extends GuiSlot {
    public GuiAccountList(Minecraft mc) {
      super(
          mc,
          GuiAccountManager.this.accountListWidth,
          GuiAccountManager.this.height,
          GuiAccountManager.this.contentTop,
          GuiAccountManager.this.contentBottom,
          28);
      setSlotXBoundsFromLeft(GuiAccountManager.this.accountListLeft);
    }

    @Override
    protected int getSize() {
      return AccountManager.accounts.size();
    }

    @Override
    protected boolean isSelected(int slotIndex) {
      return slotIndex == GuiAccountManager.this.selectedAccount;
    }

    @Override
    protected int getScrollBarX() {
      return GuiAccountManager.this.accountListLeft + GuiAccountManager.this.accountListWidth - 6;
    }

    @Override
    public int getListWidth() {
      return Math.max(20, GuiAccountManager.this.accountListWidth - 10);
    }

    @Override
    protected int getContentHeight() {
      return AccountManager.accounts.size() * 28;
    }

    @Override
    protected void elementClicked(int slotIndex, boolean isDoubleClick, int mouseX, int mouseY) {
      GuiAccountManager.this.selectedAccount = slotIndex;
      GuiAccountManager.this.updateScreen();
      if (isDoubleClick) {
        GuiAccountManager.this.actionPerformed(loginButton);
      }
    }

    @Override
    protected void drawBackground() {
      GuiAccountManager.this.drawDefaultBackground();
    }

    @Override
    protected void drawSlot(int entryID, int x, int y, int k, int mouseXIn, int mouseYIn) {
      FontRenderer fr = GuiAccountManager.this.fontRendererObj;
      Account account = AccountManager.accounts.get(entryID);
      int rowWidth = getListWidth() - 4;
      Gui.drawRect(x, y, x + rowWidth, y + 24,
          entryID == GuiAccountManager.this.selectedAccount ? 0x8A16445A : 0x72000000);
      GuiAccountManager.this.drawLabelValue(
          fr, "Account: ", GuiAccountManager.this.displayUsername(account),
          x + 4, y + 3, rowWidth - 8);
      GuiAccountManager.this.drawLabelValue(
          fr, "Type: ", GuiAccountManager.this.displayType(account),
          x + 4, y + 14, rowWidth - 8);
    }
  }
}
