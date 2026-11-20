package dev.idle.igam.gui;

import dev.idle.igam.AccountManager;
import dev.idle.igam.auth.Account;
import dev.idle.igam.auth.RefreshTokenAuth;
import dev.idle.igam.utils.Notification;
import dev.idle.igam.utils.TextFormatting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

public class GuiRefreshTokenLogin extends GuiScreen {
  private static final Pattern REFRESH_TOKEN = Pattern.compile("M\\.C[A-Za-z0-9._!*$\\-]{47,}");
  private final GuiScreen previousScreen;
  private GuiTextField tokenField;
  private GuiButton importButton;
  private GuiButton cancelButton;
  private ExecutorService executor;
  private CompletableFuture<Void> task;
  private String status = "&7Paste one or more Microsoft refresh tokens.&r";

  public GuiRefreshTokenLogin(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    Keyboard.enableRepeatEvents(true);
    int fieldWidth = Math.min(360, width - 40);
    tokenField = new GuiTextField(
        0, fontRendererObj, width / 2 - fieldWidth / 2, height / 2 - 12, fieldWidth, 20);
    tokenField.setMaxStringLength(65535);
    tokenField.setFocused(true);
    buttonList.add(importButton = new GuiButton(
        0, width / 2 - 102, height / 2 + 18, 100, 20, "Import"));
    buttonList.add(cancelButton = new GuiButton(
        1, width / 2 + 2, height / 2 + 18, 100, 20, "Cancel"));
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
    tokenField.updateCursorCounter();
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    drawCenteredString(fontRendererObj, "Refresh Token Import", width / 2, height / 2 - 52, -1);
    drawCenteredString(
        fontRendererObj, TextFormatting.translate(status), width / 2, height / 2 - 36, -1);
    drawCenteredString(
        fontRendererObj,
        "Separate tokens with spaces, commas, or new lines",
        width / 2,
        height / 2 - 24,
        0xFFAAAAAA);
    tokenField.drawTextBox();
    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  @Override
  protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
    super.mouseClicked(mouseX, mouseY, mouseButton);
    tokenField.mouseClicked(mouseX, mouseY, mouseButton);
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      actionPerformed(cancelButton);
      return;
    }
    if (isKeyComboCtrlV(keyCode)) {
      tokenField.writeText(getClipboardString().replaceAll("\\s+", " "));
    } else {
      tokenField.textboxKeyTyped(typedChar, keyCode);
    }
    if (keyCode == Keyboard.KEY_RETURN) {
      actionPerformed(importButton);
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button == null || !button.enabled) {
      return;
    }
    if (button.id == 1) {
      mc.displayGuiScreen(previousScreen);
      return;
    }
    if (button.id == 0) {
      startImport(tokenField.getText());
    }
  }

  private void startImport(String input) {
    if (task != null && !task.isDone()) {
      return;
    }
    List<String> tokens = extractRefreshTokens(input);
    if (tokens.isEmpty()) {
      status = "&cNo valid refresh tokens found.&r";
      return;
    }
    if (executor == null || executor.isShutdown()) {
      AtomicInteger threadNumber = new AtomicInteger();
      executor = Executors.newFixedThreadPool(
          Math.min(3, tokens.size()),
          runnable -> {
            Thread thread = new Thread(
                runnable, "Mimeow-Refresh-Import-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
          });
    }

    importButton.enabled = false;
    tokenField.setEnabled(false);
    status = "&eImporting " + tokens.size() + " account(s)...&r";
    List<Account> imported = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger failures = new AtomicInteger();
    CompletableFuture<?>[] imports = new CompletableFuture<?>[tokens.size()];
    for (int index = 0; index < tokens.size(); index++) {
      imports[index] = RefreshTokenAuth.authenticate(tokens.get(index), executor)
          .thenAccept(imported::add)
          .exceptionally(error -> {
            failures.incrementAndGet();
            return null;
          });
    }

    task = CompletableFuture.allOf(imports)
        .thenRunAsync(() -> AccountManager.storeAll(new ArrayList<>(imported)), executor)
        .whenComplete((ignored, error) -> mc.addScheduledTask(
            () -> finishImport(imported.size(), failures.get(), error)));
  }

  private void finishImport(int successes, int failures, Throwable error) {
    if (mc.currentScreen != this) {
      return;
    }
    if (error != null || successes == 0) {
      status = error == null
          ? "&cFailed to import " + failures + " account(s).&r"
          : "&cRefresh-token import failed.&r";
      importButton.enabled = true;
      tokenField.setEnabled(true);
      return;
    }

    String message = failures == 0
        ? "&aImported " + successes + " account(s).&r"
        : "&aImported " + successes + " account(s), &cfailed " + failures + ".&r";
    if (previousScreen instanceof GuiAccountManager) {
      ((GuiAccountManager) previousScreen).setNotification(
          new Notification(TextFormatting.translate(message), 5000L));
    }
    mc.displayGuiScreen(previousScreen);
  }

  static List<String> extractRefreshTokens(String input) {
    if (input == null || input.trim().isEmpty()) {
      return Collections.emptyList();
    }
    Set<String> tokens = new LinkedHashSet<>();
    Matcher matcher = REFRESH_TOKEN.matcher(input);
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return new ArrayList<>(tokens);
  }
}
