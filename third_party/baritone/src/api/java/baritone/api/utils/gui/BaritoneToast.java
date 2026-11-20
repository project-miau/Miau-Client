/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.utils.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.IChatComponent;

/** Baritone's popup notification adapted to the 1.8.9 HUD. */
public final class BaritoneToast {
    private static String title;
    private static String subtitle;
    private static long expiresAt;
    private BaritoneToast() {}
    public static void addOrUpdate(IChatComponent titleComponent, IChatComponent message, long duration) {
        title = titleComponent.getFormattedText();
        subtitle = message == null ? null : message.getFormattedText();
        expiresAt = System.currentTimeMillis() + Math.max(0, duration);
    }
    public static void render() {
        if (title == null || System.currentTimeMillis() >= expiresAt) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(minecraft);
        int width = Math.max(150, Math.max(minecraft.fontRendererObj.getStringWidth(title),
                subtitle == null ? 0 : minecraft.fontRendererObj.getStringWidth(subtitle)) + 16);
        int x = resolution.getScaledWidth() - width - 6;
        Gui.drawRect(x, 6, x + width, subtitle == null ? 29 : 41, 0xDD202028);
        minecraft.fontRendererObj.drawStringWithShadow(title, x + 8, 13, 0xFFFFFFFF);
        if (subtitle != null) minecraft.fontRendererObj.drawStringWithShadow(subtitle, x + 8, 26, 0xFFBBBBCC);
    }
}
