package dev.idle.igam.gui;

import dev.idle.igam.AccountManager;
import dev.idle.igam.auth.MinecraftMicrosoftAuth;
import dev.idle.igam.auth.SessionManager;
import dev.idle.igam.utils.Notification;
import dev.idle.igam.utils.SystemUtils;
import dev.idle.igam.utils.TextFormatting;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import org.lwjgl.input.Keyboard;

public class GuiMicrosoftDeviceCode extends GuiScreen {
  private final GuiScreen previousScreen;
  private GuiButton openButton;
  private GuiButton cancelButton;
  private ExecutorService executor;
  private CompletableFuture<Void> task;
  private volatile String status;
  private volatile String loginUrl;
  private volatile boolean success;
  private volatile boolean closed;

  public GuiMicrosoftDeviceCode(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    openButton = new GuiButton(1, width / 2 - 75, height / 2 + 35, 150, 20, "Open Browser");
    openButton.enabled = loginUrl != null;
    buttonList.add(openButton);
    buttonList.add(
        cancelButton = new GuiButton(0, width / 2 - 50, height / 2 + 60, 100, 20, "Cancel"));
    if (task == null) {
      status = "&fRequesting Microsoft login code...&r";
      executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Miau Microsoft Login");
        thread.setDaemon(true);
        return thread;
      });
      startLogin();
    }
  }

  private void startLogin() {
    task = CompletableFuture
        .supplyAsync(() -> {
          try {
            return MinecraftMicrosoftAuth.login(this::showDeviceCode);
          } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CompletionException(error);
          } catch (Exception error) {
            throw new CompletionException(error);
          }
        }, executor)
        .thenAccept(result -> runOnMainThread(() -> completeLogin(result)))
        .exceptionally(error -> {
          runOnMainThread(() -> failLogin(error));
          return null;
        });
  }

  private void showDeviceCode(MsaDeviceCode deviceCode) {
    loginUrl = deviceCode.getDirectVerificationUri();
    SystemUtils.setClipboard(loginUrl);
    SystemUtils.openWebLink(URI.create(loginUrl));
    runOnMainThread(() -> {
      if (closed) return;
      openButton.enabled = true;
      status = "&fEnter code &b" + deviceCode.getUserCode()
          + "&f in Microsoft. The link was copied.&r";
    });
  }

  private void completeLogin(MinecraftMicrosoftAuth.LoginResult result) {
    if (closed) return;
    AccountManager.store(result.toAccount());
    SessionManager.set(result.getSession());
    success = true;
  }

  private void failLogin(Throwable error) {
    if (closed) return;
    status = "&c" + MinecraftMicrosoftAuth.describe(error) + "&r";
  }

  private void runOnMainThread(Runnable action) {
    mc.addScheduledTask(action);
  }

  @Override
  public void onGuiClosed() {
    closed = true;
    if (task != null && !task.isDone()) {
      task.cancel(true);
    }
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public void updateScreen() {
    if (!success) return;
    String username = SessionManager.get().getUsername();
    success = false;
    mc.displayGuiScreen(
        new GuiAccountManager(
            previousScreen,
            new Notification(
                TextFormatting.translate(
                    String.format("&aSuccessful login! (%s)&r", username)),
                5000L)));
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    super.drawScreen(mouseX, mouseY, partialTicks);
    drawCenteredString(
        fontRendererObj, "Microsoft Authentication", width / 2, height / 2 - 55, 0xFFAAAAAA);
    drawWrappedStatus();
  }

  private void drawWrappedStatus() {
    if (status == null) return;
    List<String> lines = fontRendererObj.listFormattedStringToWidth(
        TextFormatting.translate(status), Math.max(100, width - 40));
    int y = height / 2 - 15 - lines.size() * fontRendererObj.FONT_HEIGHT / 2;
    for (String line : lines) {
      drawCenteredString(fontRendererObj, line, width / 2, y, 0xFFFFFFFF);
      y += fontRendererObj.FONT_HEIGHT + 2;
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    if (keyCode == Keyboard.KEY_ESCAPE) {
      actionPerformed(cancelButton);
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button == null || !button.enabled) return;
    if (button.id == 1 && loginUrl != null) {
      SystemUtils.setClipboard(loginUrl);
      SystemUtils.openWebLink(URI.create(loginUrl));
    } else if (button.id == 0) {
      mc.displayGuiScreen(new GuiAccountManager(previousScreen));
    }
  }
}
