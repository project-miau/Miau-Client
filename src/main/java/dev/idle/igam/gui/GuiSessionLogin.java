package dev.idle.igam.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import dev.idle.igam.auth.SessionManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class GuiSessionLogin extends GuiScreen {
  private GuiScreen previousScreen;

  private volatile String status = "Session Login";
  private GuiTextField sessionField;
  private ScaledResolution sr;
  private GuiButton loginButton;
  private volatile boolean loginInProgress;

  public GuiSessionLogin(GuiScreen previousScreen) {
    this.previousScreen = previousScreen;
  }

  @Override
  public void initGui() {
    Keyboard.enableRepeatEvents(true);
    sr = new ScaledResolution(mc);

    sessionField =
        new GuiTextField(
            1,
            mc.fontRendererObj,
            sr.getScaledWidth() / 2 - 100,
            sr.getScaledHeight() / 2,
            200,
            20);
    sessionField.setMaxStringLength(32767);
    sessionField.setFocused(true);

    loginButton =
        new GuiButton(
            998, sr.getScaledWidth() / 2 - 100, sr.getScaledHeight() / 2 + 30, 200, 20, "Login");
    buttonList.add(loginButton);

    super.initGui();
  }

  @Override
  public void onGuiClosed() {
    Keyboard.enableRepeatEvents(false);

    super.onGuiClosed();
  }

  @Override
  public void drawScreen(int mouseX, int mouseY, float partialTicks) {
    drawDefaultBackground();

    mc.fontRendererObj.drawString(
        status,
        sr.getScaledWidth() / 2 - mc.fontRendererObj.getStringWidth(status) / 2,
        sr.getScaledHeight() / 2 - 30,
        Color.WHITE.getRGB());
    sessionField.drawTextBox();

    super.drawScreen(mouseX, mouseY, partialTicks);
  }

  @Override
  protected void actionPerformed(GuiButton button) throws IOException {
    if (button.id == 998 && !loginInProgress) {
      final String input = sessionField.getText().trim();
      loginInProgress = true;
      loginButton.enabled = false;
      status = "§7Checking session...";

      Thread worker =
          new Thread(
              () -> {
                try {
                  Session session = resolveSession(input);
                  mc.addScheduledTask(
                      () -> {
                        if (mc.currentScreen != this) return;
                        SessionManager.set(session);
                        loginInProgress = false;
                        mc.displayGuiScreen(previousScreen);
                      });
                }
                catch (Exception exception) {
                  mc.addScheduledTask(
                      () -> {
                        if (mc.currentScreen != this) return;
                        String message = String.valueOf(exception.getMessage());
                        status = message.contains("401")
                            ? "§cError: Invalid session."
                            : "§cError: Couldn't set session (check mc logs)";
                        loginInProgress = false;
                        loginButton.enabled = true;
                      });
                }
              },
              "Mimeow-Session-Login");
      worker.setDaemon(true);
      worker.start();
    }

    super.actionPerformed(button);
  }

  private Session resolveSession(String input) throws Exception {
    String[] parts = input.split(":", 3);
    if (parts.length == 3) {
      if (parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
        throw new IllegalArgumentException("Incomplete session");
      }
      return new Session(parts[0], parts[1], parts[2], "mojang");
    }
    if (input.isEmpty()) {
      throw new IllegalArgumentException("Empty session");
    }

    HttpURLConnection connection =
        (HttpURLConnection)
            new URL("https://api.minecraftservices.com/minecraft/profile/").openConnection();
    try {
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("Authorization", "Bearer " + input);
      connection.setConnectTimeout(10_000);
      connection.setReadTimeout(15_000);
      connection.setUseCaches(false);
      try (InputStream stream = connection.getInputStream();
           InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
        return new Session(
            json.get("name").getAsString(),
            json.get("id").getAsString(),
            input,
            "mojang");
      }
    }
    finally {
      connection.disconnect();
    }
  }

  @Override
  protected void keyTyped(char typedChar, int keyCode) throws IOException {
    sessionField.textboxKeyTyped(typedChar, keyCode);

    if (Keyboard.KEY_ESCAPE == keyCode) mc.displayGuiScreen(previousScreen);
    else super.keyTyped(typedChar, keyCode);
  }
}
