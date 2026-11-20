package dev.idle.igam.gui;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import dev.idle.igam.AccountManager;
import dev.idle.igam.auth.Account;
import dev.idle.igam.auth.MicrosoftAuth;
import dev.idle.igam.auth.RefreshTokenAuth;
import dev.idle.igam.auth.SessionManager;
import dev.idle.igam.utils.Notification;
import dev.idle.igam.utils.TextFormatting;
import net.minecraft.client.gui.*;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiAddToken extends GuiScreen {
  private final GuiScreen previousScreen;
  private String status = null;
  private ExecutorService executor = null;
  private CompletableFuture<Void> task = null;
  private boolean success = false;
  private GuiTextField tokenField;

  public GuiAddToken(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  @Override
  public void initGui() {
    buttonList.clear();
    Keyboard.enableRepeatEvents(true);
    ScaledResolution sr = new ScaledResolution(mc);
    tokenField =
        new GuiTextField(
            1,
            mc.fontRendererObj,
            sr.getScaledWidth() / 2 - 100,
            sr.getScaledHeight() / 2,
            200,
            20);
    tokenField.setMaxStringLength(32767);
    tokenField.setFocused(true);

    buttonList.add(
        new GuiButton(
            998, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 30, 200, 20, "Add"));
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
    if (success) {
      mc.displayGuiScreen(
          new GuiAccountManager(
              previousScreen,
              new Notification(
                  TextFormatting.translate(
                      String.format(
                          "&aSuccessful login! (%s)&r", SessionManager.get().getUsername())),
                  5000L)));
      success = false;
    }
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();
    super.drawScreen(mouseX, mouseY, partialTicks);

    drawCenteredString(
        fontRendererObj,
        "Token / Refresh / Session",
        width / 2,
        height / 2 - fontRendererObj.FONT_HEIGHT / 2 - fontRendererObj.FONT_HEIGHT * 2 - 14,
        11184810);
    tokenField.drawTextBox();

    if (status != null) {
      drawCenteredString(
          fontRendererObj,
          TextFormatting.translate(status),
          width / 2,
          height / 2 - fontRendererObj.FONT_HEIGHT / 2 - 14,
          -1);
    }

  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) {
    tokenField.textboxKeyTyped(typedChar, keyCode);
    if (keyCode == Keyboard.KEY_ESCAPE) {
      if (task == null || task.isDone() || task.isCancelled() || task.isCompletedExceptionally()) {
        mc.displayGuiScreen(previousScreen);
      }
    } else if (keyCode == Keyboard.KEY_RETURN && !buttonList.isEmpty()) {
      actionPerformed(buttonList.get(0));
    }
  }

  @Override
  protected void actionPerformed(GuiButton button) {
    if (button == null) {
      return;
    }

    if (button.enabled) {
      if (button.id == 998) {
        if (task == null) {
          if (executor == null) {
            executor = Executors.newSingleThreadExecutor(
                runnable -> {
                  Thread thread = new Thread(runnable, "Mimeow-Token-Auth");
                  thread.setDaemon(true);
                  return thread;
                });
          }
          final String input = tokenField.getText().trim();
          if (input.isEmpty()) {
            status = "&cPlease enter a token or session.&r";
            return;
          }

          String[] parts = input.split(":", 3);
          if (parts.length == 3 && !parts[0].isEmpty()
              && !parts[1].isEmpty() && !parts[2].isEmpty()) {
            Session session = new Session(parts[0], parts[1], parts[2], "mojang");
            Account acc = new Account(
                "", parts[2], parts[0], 0L, "", "", Account.TYPE_SESSION,
                parts[1], 0L, 0L);
            AccountManager.store(acc);
            SessionManager.set(session);
            success = true;
            return;
          }

          task =
              MicrosoftAuth.login(input, executor)
                  .handle((session, error) -> {
                    if (session != null) {
                      Account account = new Account(
                          "", input, session.getUsername(), 0L, "", "", Account.TYPE_TOKEN,
                          session.getPlayerID(), 0L, 0L);
                      completeLogin(account, session);
                      return CompletableFuture.<Void>completedFuture(null);
                    }
                    status = "&7Refreshing Microsoft access token...&r";
                    return RefreshTokenAuth.authenticate(input, executor)
                        .thenAccept(account -> completeLogin(
                            account,
                            new Session(
                                account.getUsername(),
                                account.getUid(),
                                account.getAccessToken(),
                                "mojang")));
                  })
                  .thenCompose(future -> future)
                  .exceptionally(
                      error -> {
                        Throwable reason = error.getCause() == null ? error : error.getCause();
                        status = String.format("&c%s&r", safeMessage(reason));
                        task = null;
                        return null;
                      });
        }
      }
    }
  }

  private void completeLogin(Account account, Session session) {
    AccountManager.store(account);
    mc.addScheduledTask(() -> {
      status = null;
      SessionManager.set(session);
      success = true;
    });
  }

  private String safeMessage(Throwable error) {
    String message = error == null ? null : error.getMessage();
    return message == null || message.trim().isEmpty() ? "Authentication failed" : message;
  }
}
