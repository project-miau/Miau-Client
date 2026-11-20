package idle.miau.module.impl.render;

import idle.miau.module.Module;
import idle.miau.utility.IMinecraftInstance;
import idle.miau.utility.RenderUtils;
import idle.miau.utility.Utils;
import idle.miau.utility.font.MiauFontRenderer;
import idle.miau.utility.shader.BlurUtils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class ModernArrayListRenderer implements IMinecraftInstance {
    private final ModernHudSettings settings;
    private final Map<Module, RowPosition> positions = new IdentityHashMap<>();
    private long lastFrame;
    private float relativeX = Float.NaN;
    private float relativeY = Float.NaN;
    private boolean lastAlignRight = true;
    private boolean lastGrowUp;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    ModernArrayListRenderer(ModernHudSettings settings) {
        this.settings = settings;
    }

    void render(List<ArrayListModule.AnimatedRow> entries, MiauFontRenderer font,
                boolean scaleIn, boolean moveIn) {
        ScaledResolution resolution = new ScaledResolution(mc);
        syncPosition(resolution);
        long frameTime = System.nanoTime();
        float elapsed = lastFrame == 0 ? 0 : Math.max(0, (frameTime - lastFrame) / 1000000.0f);
        float animationStep = Math.min(1, elapsed * 0.015f * 250 / (float) settings.animationDuration.getInput());
        lastFrame = frameTime;
        Set<Module> modules = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ArrayListModule.AnimatedRow entry : entries) modules.add(entry.module);
        positions.keySet().retainAll(modules);
        float scale = ArrayListModule.getSelectedFontScale();
        float paddingX = (float) settings.horizontalPadding.getInput() * scale;
        float paddingY = (float) settings.verticalPadding.getInput() * scale;
        float rowHeight = ArrayListModule.getHudRowHeight(font, paddingY);
        float textPaddingY = (rowHeight - font.getTextTopOffset() - font.getTextBottomOffset()) / 2;
        ModernArrayListLayout layout = layout(entries, resolution, paddingX, rowHeight, scale, scaleIn, moveIn, animationStep);
        if (mc.currentScreen instanceof GuiChat && !settings.lockPosition.isToggled()) {
            if (handleDrag(layout, resolution, font, paddingX, rowHeight)) {
                layout = layout(entries, resolution, paddingX, rowHeight, scale, scaleIn, moveIn, 0);
            }
        } else {
            dragging = false;
        }
        if (layout.rows.isEmpty()) {
            return;
        }
        ModernArrayListLayout frame = layout;
        long now = System.currentTimeMillis();
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            if (settings.blurEnabled()) {
                BlurUtils.renderBlur(() -> drawBackground(frame, now, 255, false),
                        (int) settings.blurPasses.getInput(), (float) settings.blurRadius.getInput());
            }
            if (settings.shaderEnabled()) {
                BlurUtils.renderBloom(() -> drawEffect(frame, entries, font, textPaddingY, scaleIn, now),
                        (int) settings.effectPasses.getInput(), (float) settings.effectRadius.getInput());
            }
            if (settings.background.getInput() != 0) {
                drawBackground(frame, now, -1, false);
            }
            drawBorders(frame, now, -1);
            drawText(frame, entries, font, textPaddingY, scaleIn, now, -1);
        } finally {
            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.enableTexture2D();
            GlStateManager.depthMask(depthWrite);
            if (depthEnabled) GlStateManager.enableDepth();
            else GlStateManager.disableDepth();
            RenderUtils.setAlphaLimit(10);
            RenderUtils.resetColor();
        }
    }

    float getRelativeX() {
        syncPosition(new ScaledResolution(mc));
        return relativeX;
    }

    float getRelativeY() {
        syncPosition(new ScaledResolution(mc));
        return relativeY;
    }

    void setRelativePosition(float x, float y) {
        if (Float.isFinite(x) && Float.isFinite(y)) {
            relativeX = Math.max(0, Math.min(1, x));
            relativeY = Math.max(0, Math.min(1, y));
            lastAlignRight = settings.alignment.getInput() == 0;
            lastGrowUp = settings.growth.getInput() == 1;
            resetAnimation();
        }
    }

    void resetAnimation() {
        positions.clear();
        lastFrame = 0;
    }

    private void syncPosition(ScaledResolution resolution) {
        boolean alignRight = settings.alignment.getInput() == 0;
        boolean growUp = settings.growth.getInput() == 1;
        if (!Float.isFinite(relativeX) || !Float.isFinite(relativeY)) {
            relativeX = alignRight ? 1 - 10.0F / Math.max(1, resolution.getScaledWidth())
                    : 10.0F / Math.max(1, resolution.getScaledWidth());
            relativeY = growUp ? 1 - 10.0F / Math.max(1, resolution.getScaledHeight())
                    : 10.0F / Math.max(1, resolution.getScaledHeight());
        } else {
            if (lastAlignRight != alignRight) relativeX = 1 - relativeX;
            if (lastGrowUp != growUp) relativeY = 1 - relativeY;
            if (lastAlignRight != alignRight || lastGrowUp != growUp) resetAnimation();
        }
        lastAlignRight = alignRight;
        lastGrowUp = growUp;
    }

    private ModernArrayListLayout layout(List<ArrayListModule.AnimatedRow> entries,
                                         ScaledResolution resolution, float paddingX,
                                         float rowHeight, float scale, boolean scaleIn, boolean moveIn,
                                         float animationStep) {
        float[] widths = new float[entries.size()];
        float[] progress = new float[entries.size()];
        boolean[] enabled = new boolean[entries.size()];
        float widest = 0;
        float height = 0;
        float lastVisibility = 0;
        float gap = (float) settings.rowGap.getInput() * scale;
        for (int i = 0; i < entries.size(); i++) {
            widths[i] = entries.get(i).width;
            progress[i] = entries.get(i).progress;
            enabled[i] = entries.get(i).module.isEnabled();
            float visibility = Math.max(0, Math.min(1, progress[i]));
            if (visibility > 0) {
                widest = Math.max(widest, widths[i]);
            }
            if (enabled[i] && visibility > 0) {
                height += rowHeight + gap;
                lastVisibility = 1;
            }
        }
        height -= gap * lastVisibility;
        widest = Math.max(widest, (float) settings.minimumWidth.getInput() * scale - paddingX * 2);
        float thickness = settings.outline.getInput() == 0 ? 0 : (float) settings.outlineWidth.getInput() * scale;
        float minX = paddingX + thickness + (lastAlignRight ? widest : 0);
        float maxX = resolution.getScaledWidth() - paddingX - thickness - (lastAlignRight ? 0 : widest);
        float x = relativeX * resolution.getScaledWidth();
        x = minX <= maxX ? Math.max(minX, Math.min(maxX, x)) : lastAlignRight ? maxX : paddingX + thickness;
        relativeX = x / Math.max(1, resolution.getScaledWidth());
        float minY = thickness + (lastGrowUp ? height : 0);
        float maxY = resolution.getScaledHeight() - thickness - (lastGrowUp ? 0 : height);
        float y = relativeY * resolution.getScaledHeight();
        y = minY <= maxY ? Math.max(minY, Math.min(maxY, y))
                : lastGrowUp ? resolution.getScaledHeight() - thickness : thickness;
        relativeY = y / Math.max(1, resolution.getScaledHeight());
        float anchorX = x;
        float anchorY = y;
        return new ModernArrayListLayout(widths, progress,
                x, y, lastAlignRight, paddingX, rowHeight, gap,
                thickness,
                (int) settings.outline.getInput(), (int) settings.sidebar.getInput(),
                scaleIn, moveIn, resolution.getScaledWidth(), lastGrowUp,
                settings.widthMode.getInput() == 1, (float) settings.minimumWidth.getInput() * scale,
                (int) settings.textAlignment.getInput(), enabled,
                row -> animateRow(row, entries.get(row.index).module, anchorX, anchorY,
                        scaleIn || moveIn, animationStep));
    }

    private ModernArrayListLayout.Row animateRow(ModernArrayListLayout.Row row, Module module,
                                                float anchorX, float anchorY, boolean animated, float step) {
        float targetX = row.textX - anchorX;
        float targetY = row.top - anchorY;
        RowPosition position = positions.computeIfAbsent(module, key -> new RowPosition(targetX, targetY));
        position.x = animated ? approach(position.x, targetX, step) : targetX;
        position.y = animated ? approach(position.y, targetY, step) : targetY;
        return row.translate(position.x - targetX, position.y - targetY);
    }

    private static float approach(float current, float target, float step) {
        return Math.abs(current - target) <= 0.5f ? target : current + (target - current) * step;
    }

    private static final class RowPosition {
        private float x;
        private float y;

        private RowPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private boolean handleDrag(ModernArrayListLayout layout, ScaledResolution resolution,
                               MiauFontRenderer font, float paddingX, float rowHeight) {
        float mouseX = Mouse.getX() * resolution.getScaledWidth() / (float) mc.displayWidth;
        float mouseY = resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1;
        float x = relativeX * resolution.getScaledWidth();
        float y = relativeY * resolution.getScaledHeight();
        float left = layout.left;
        float right = layout.right;
        float bottom = layout.bottom;
        float top = layout.top;
        if (layout.rows.isEmpty()) {
            float width = font.getStringWidth("ArrayList") + paddingX * 2;
            left = lastAlignRight ? x - width : x - paddingX;
            right = lastAlignRight ? x + paddingX : x + width;
            top = lastGrowUp ? y - rowHeight : y;
            bottom = top + rowHeight;
        }
        if (!Mouse.isButtonDown(0)) {
            dragging = false;
            return false;
        }
        if (!dragging && mouseX >= left && mouseX <= right && mouseY >= top && mouseY <= bottom) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
        if (!dragging) {
            return false;
        }
        float minX = Math.max(paddingX, x - left);
        float maxX = resolution.getScaledWidth() - Math.max(paddingX, right - x);
        float nextX = Math.max(Math.min(minX, maxX), Math.min(maxX, mouseX - dragOffsetX));
        float minY = Math.max(0, y - top);
        float maxY = resolution.getScaledHeight() - Math.max(0, bottom - y);
        float nextY = Math.max(Math.min(minY, maxY), Math.min(maxY, mouseY - dragOffsetY));
        relativeX = nextX / Math.max(1, resolution.getScaledWidth());
        relativeY = nextY / Math.max(1, resolution.getScaledHeight());
        return true;
    }

    private void drawEffect(ModernArrayListLayout layout, List<ArrayListModule.AnimatedRow> entries,
                            MiauFontRenderer font, float paddingY, boolean scaleIn, long now) {
        int target = (int) settings.effectTarget.getInput();
        int alpha = (int) settings.effectOpacity.getInput();
        float scale = ArrayListModule.getSelectedFontScale();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(settings.effectOffsetX.getInput() * scale,
                    settings.effectOffsetY.getInput() * scale, 0);
            if (target == 0 || target == 3) drawBackground(layout, now, alpha, true);
            if (target == 1 || target == 3) drawText(layout, entries, font, paddingY, scaleIn, now, alpha);
            if (target == 2 || target == 3) drawBorders(layout, now, alpha);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private void drawBackground(ModernArrayListLayout layout, long now, int maskAlpha, boolean effect) {
        float radius = (float) settings.rounding.getInput() * ArrayListModule.getSelectedFontScale();
        for (ModernArrayListLayout.Row row : layout.rows) {
            int alpha = Math.round((maskAlpha < 0 ? (float) settings.backgroundAlpha.getInput() : maskAlpha)
                    * row.progress);
            int primary = accent(row, now);
            int color = settings.backgroundColor.getColor();
            if (effect) {
                color = effectColor(primary, primary);
            } else if (maskAlpha >= 0) {
                color = 0xFFFFFFFF;
            } else if (settings.background.getInput() == 2
                    || settings.background.getInput() == 3 && settings.backgroundGradient.getInput() == 0) {
                color = mix(color, primary, (float) settings.backgroundTint.getInput() / 100);
            }
            color = Utils.mergeAlpha(color, alpha);
            float rounding = Math.min(radius, Math.min(row.right - row.left, row.bottom - row.top) / 2);
            if (maskAlpha < 0 && settings.background.getInput() == 3) {
                int secondary = settings.backgroundGradient.getInput() == 1
                        ? settings.backgroundColor2.getColor()
                        : mix(settings.backgroundColor.getColor(),
                        ArrayListModule.getHudColorAtPosition(row.right, row.bottom + 40, now),
                        (float) settings.backgroundTint.getInput() / 100);
                int[] corners = gradientCorners(color, Utils.mergeAlpha(secondary, alpha),
                        (int) settings.gradientDirection.getInput());
                RenderUtils.drawRoundedGradientRect(row.left, row.top, row.right, row.bottom,
                        rounding, corners[0], corners[1], corners[2], corners[3]);
            } else if (rounding > 0) {
                RenderUtils.drawRoundedRectangle(row.left, row.top, row.right, row.bottom, rounding, color);
            } else {
                RenderUtils.drawRect(row.left, row.top, row.right, row.bottom, color);
            }
        }
    }

    private void drawBorders(ModernArrayListLayout layout, long now, int maskAlpha) {
        for (ModernArrayListLayout.Border border : layout.borders) {
            int theme = ArrayListModule.getHudColorAtPosition((border.left + border.right) / 2, border.top, now);
            int color = settings.outlineColorMode.getInput() == 0 ? theme
                    : settings.outlineColorMode.getInput() == 1 ? 0xFFFFFFFF : settings.outlineColor.getColor();
            if (maskAlpha >= 0) color = effectColor(color, theme);
            float progress = Math.max(0, Math.min(1, layout.progress[border.row]));
            color = Utils.mergeAlpha(color, Math.round((maskAlpha < 0
                    ? (float) settings.outlineOpacity.getInput() : maskAlpha) * progress));
            RenderUtils.drawRect(border.left, border.top, border.right, border.bottom, color);
        }
    }

    private void drawText(ModernArrayListLayout layout, List<ArrayListModule.AnimatedRow> entries,
                          MiauFontRenderer font, float paddingY, boolean scaleIn, long now, int maskAlpha) {
        for (ModernArrayListLayout.Row row : layout.rows) {
            int alpha = Math.round((maskAlpha < 0 ? (float) settings.textOpacity.getInput() : maskAlpha) * row.progress);
            int suffixAlpha = Math.round((maskAlpha < 0 ? (float) settings.suffixOpacity.getInput() : maskAlpha) * row.progress);
            if (Math.max(alpha, suffixAlpha) < 4) {
                continue;
            }
            ArrayListModule.AnimatedRow entry = entries.get(row.index);
            float scale = ArrayListModule.getSelectedFontScale();
            float textX = row.textX + (float) settings.textOffsetX.getInput() * scale;
            float y = row.top + paddingY + font.getTextTopOffset() + (float) settings.textOffsetY.getInput() * scale;
            float pivotX = row.outerEdge + (lastAlignRight
                    ? -(float) settings.horizontalPadding.getInput() : (float) settings.horizontalPadding.getInput())
                    * ArrayListModule.getSelectedFontScale();
            boolean clip = !scaleIn && row.progress < 1;
            if (clip) {
                float clipX = maskAlpha < 0 ? 0 : (float) settings.effectOffsetX.getInput() * scale;
                float clipY = maskAlpha < 0 ? 0 : (float) settings.effectOffsetY.getInput() * scale;
                RenderUtils.scissorPushGui(row.left + clipX, row.top + clipY,
                        row.right - row.left, row.bottom - row.top);
            }
            if (scaleIn) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(pivotX, row.top, 0);
                GlStateManager.scale(row.progress, row.progress, 1);
                GlStateManager.translate(-pivotX, -row.top, 0);
            }
            try {
                boolean shadow = maskAlpha < 0 && ArrayListModule.isHudTextShadowEnabled();
                int theme = accent(row, now);
                int nameColor = settings.textColorMode.getInput() == 0 ? theme
                        : settings.textColorMode.getInput() == 1 ? 0xFFFFFFFF : settings.textColor.getColor();
                int suffixColor = settings.suffixColorMode.getInput() == 0 ? 0xFFAAAAAA
                        : settings.suffixColorMode.getInput() == 1 ? theme
                        : settings.suffixColorMode.getInput() == 2 ? 0xFFFFFFFF : settings.suffixColor.getColor();
                if (maskAlpha >= 0) {
                    nameColor = effectColor(nameColor, theme);
                    suffixColor = effectColor(suffixColor, theme);
                }
                if (alpha >= 4 && settings.gradientText.isToggled() && settings.textColorMode.getInput() == 0
                        && (maskAlpha < 0 || settings.effect.getInput() == 2 && settings.effectColorMode.getInput() != 2)) {
                    font.drawGlyphString(entry.name, textX, y,
                            (character, offset, width, formatting) -> Utils.mergeAlpha(
                                    ArrayListModule.getHudColorAtPosition(textX + offset, row.top, now), alpha),
                            shadow);
                } else if (alpha >= 4) {
                    font.drawString(entry.name, textX, y, Utils.mergeAlpha(nameColor, alpha), shadow);
                }
                if (suffixAlpha >= 4) {
                    font.drawString(entry.suffix, textX + entry.suffixOffset, y,
                            Utils.mergeAlpha(suffixColor, suffixAlpha), shadow);
                }
            } finally {
                if (scaleIn) GlStateManager.popMatrix();
                if (clip) RenderUtils.scissorPop();
            }
        }
    }

    private int accent(ModernArrayListLayout.Row row, long now) {
        return ArrayListModule.getHudColorAtPosition((row.left + row.right) / 2, row.top, now);
    }

    private int effectColor(int source, int theme) {
        if (settings.effectColorMode.getInput() == 2) return settings.effectColor.getColor();
        if (settings.effectColorMode.getInput() == 1) return theme;
        return settings.effect.getInput() == 1 ? 0xFF000000 : source;
    }

    static int[] gradientCorners(int first, int second, int direction) {
        int middle = Utils.mergeAlpha(mix(first, second, 0.5F), ((first >>> 24) + (second >>> 24)) / 2);
        switch (direction) {
            case 1: return new int[]{second, second, first, first};
            case 2: return new int[]{first, second, second, first};
            case 3: return new int[]{second, first, first, second};
            case 4: return new int[]{first, middle, second, middle};
            case 5: return new int[]{second, middle, first, middle};
            default: return new int[]{first, first, second, second};
        }
    }

    static int mix(int first, int second, float amount) {
        float factor = Math.max(0, Math.min(1, amount));
        int red = Math.round((first >> 16 & 255) * (1 - factor) + (second >> 16 & 255) * factor);
        int green = Math.round((first >> 8 & 255) * (1 - factor) + (second >> 8 & 255) * factor);
        int blue = Math.round((first & 255) * (1 - factor) + (second & 255) * factor);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
