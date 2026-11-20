package idle.miau.module.impl.render;

import idle.miau.clickgui.panel.CanvasFonts;
import idle.miau.clickgui.panel.DrawPlan;
import idle.miau.clickgui.panel.RenderAccess;
import idle.miau.module.Module;
import idle.miau.module.ModuleManager;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.utility.inventory.InventoryActions;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompactArrayListRenderer {
    private final GroupSetting settingsGroup = new GroupSetting("Overlay settings");
    private final ButtonSetting showCombat;
    private final ButtonSetting showMove;
    private final ButtonSetting showBlock;
    private final ButtonSetting showVisual;
    private final ButtonSetting showUtility;
    private final SliderSetting textSize;
    private final SliderSetting paddingX;
    private final SliderSetting paddingY;
    private final SliderSetting bgRounding;
    private final ColorSetting nameColor;
    private final ColorSetting detailsColor;
    private final ButtonSetting lowercase;
    private final ButtonSetting showDetails;
    private final ButtonSetting requireBound;
    private final ArrayListModule owner;
    private final Map<String, Setting> options = new LinkedHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private boolean dirty;
    private boolean measure;
    private int mask;
    private double lastFrame;

    public CompactArrayListRenderer(ArrayListModule owner) {
        this.owner = owner;
        owner.registerOverlaySetting(settingsGroup, "group");
        option("show_combat", showCombat = new ButtonSetting("Combat", true));
        option("show_move", showMove = new ButtonSetting("Move", true));
        option("show_block", showBlock = new ButtonSetting("Block", true));
        option("show_visual", showVisual = new ButtonSetting("Visual", true));
        option("show_utility", showUtility = new ButtonSetting("Utility", true));
        option("text_size", textSize = new SliderSetting("Text size", 16.0, 10.0, 24.0, 0.1));
        option("padding_x", paddingX = new SliderSetting("Padding X", 3.0, 0.0, 5.0, 0.1));
        option("padding_y", paddingY = new SliderSetting("Padding Y", 1.0, 0.0, 5.0, 0.1));
        option("bg_rounding", bgRounding = new SliderSetting("Rounding", 6.0, 0.0, 16.0, 0.1));
        option("name_color", nameColor = new ColorSetting("Name color", 243, 93, 18));
        option("details_color", detailsColor = new ColorSetting("Details color", 153, 153, 153));
        option("lowercase", lowercase = new ButtonSetting("Lowercase", true));
        option("show_details", showDetails = new ButtonSetting("Show details", true));
        option("require_bound", requireBound = new ButtonSetting("Only bound modules", false));
    }

    private void option(String key, Setting setting) {
        options.put(key, setting);
        ModernHudSettings.setGroup(setting, settingsGroup);
        owner.registerOverlaySetting(setting, key);
    }

    public void updateVisibility(boolean visible) {
        settingsGroup.setVisible(visible, owner);
        for (Setting setting : options.values()) {
            setting.setVisible(visible, owner);
        }
        invalidate();
    }

    public void render() {
        Minecraft mc = Minecraft.getMinecraft();
        double now = RenderAccess.now();
        float elapsed = lastFrame == 0.0 ? 0.0F : (float) (now - lastFrame);
        lastFrame = now;
        collect();
        DrawPlan frame = plan(mc.displayWidth, elapsed);
        int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, mc.displayWidth, mc.displayHeight, 0.0, -3000.0, 3000.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            RenderAccess.execute(frame);
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrixMode);
        }
    }

    private boolean category(Module m) {
        switch (m.moduleCategory()) {
            case combat:
                return showCombat.isToggled();
            case movement:
                return showMove.isToggled();
            case render:
                return showVisual.isToggled();
            case world:
                return showBlock.isToggled();
            case player:
                return showUtility.isToggled();
            default:
                return true;
        }
    }

    private boolean eligible(Module m, boolean requireEnabled) {
        return (!requireEnabled || m.isEnabled())
                && !m.isHidden()
                && (!requireBound.isToggled() || m.getKeycode() != 0)
                && this.category(m);
    }

    private void text(CompactArrayListRenderer.Row row) {
        row.name =
                bounded(
                        lowercase.isToggled()
                                ? lowercase(row.module.getName())
                                : row.module.getName(),
                        19);
        String status = showDetails.isToggled() ? row.module.getInfo() : null;
        row.status =
                status == null
                        ? ""
                        : bounded(lowercase.isToggled() ? lowercase(status) : status, 31);
    }

    private static String bounded(String text, int max) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= max ? text : new String(bytes, 0, max, StandardCharsets.UTF_8);
    }

    private static String lowercase(String source) {
        StringBuilder out = new StringBuilder(source.length());

        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            out.append(c >= 'A' && c <= 'Z' ? (char) (c + ' ') : c);
        }

        return out.toString();
    }

    private void append(Module module) {
        CompactArrayListRenderer.Row row = new CompactArrayListRenderer.Row(module);
        this.text(row);
        this.rows.add(row);
    }

    public void refresh() {
        for (CompactArrayListRenderer.Row row : this.rows) {
            if (!this.eligible(row.module, false)) {
                row.enabled = false;
            }
        }

        for (Module m : ModuleManager.modules) {
            if (this.eligible(m, true)) {
                CompactArrayListRenderer.Row existing = null;

                for (CompactArrayListRenderer.Row rowx : this.rows) {
                    if (rowx.module == m) {
                        existing = rowx;
                        break;
                    }
                }

                if (existing != null) {
                    existing.enabled = true;
                } else {
                    this.append(m);
                }
            }
        }

        this.measure = true;
    }

    public void control(Module module, boolean enabled) {
        if (module != this.owner) {
            for (CompactArrayListRenderer.Row row : this.rows) {
                if (row.module == module) {
                    if (!enabled) {
                        row.enabled = false;
                    } else if (this.eligible(module, false)) {
                        row.enabled = true;
                    }

                    return;
                }
            }

            if (enabled && this.eligible(module, true)) {
                this.append(module);
                this.measure = true;
            }
        } else {
            this.rows.clear();
            if (enabled) {
                for (Module m : ModuleManager.modules) {
                    if (this.eligible(m, true)) {
                        this.append(m);
                    }
                }

                this.measure = true;
            }
        }
    }

    public void collect() {
        int next =
                (showCombat.isToggled() ? 1 : 0)
                        + (showMove.isToggled() ? 2 : 0)
                        + (showBlock.isToggled() ? 4 : 0)
                        + (showVisual.isToggled() ? 8 : 0);
        next |= (showUtility.isToggled() ? 16 : 0) | (requireBound.isToggled() ? 32 : 0);
        boolean oldDirty = this.dirty;
        if (this.mask != next || oldDirty) {
            this.refresh();
            this.mask = next;
            this.dirty = false;
        }

        if (oldDirty || !InventoryActions.inputReady()) {
            for (CompactArrayListRenderer.Row row : this.rows) {
                this.text(row);
            }

            this.measure = true;
        }
    }

    public void invalidate() {
        dirty = true;
    }

    public DrawPlan plan(float screen, float elapsed) {
        try (CanvasFonts.GuiFaceScope face = CanvasFonts.guiFace()) {
            return planWithFace(screen, elapsed);
        }
    }

    private DrawPlan planWithFace(float screen, float elapsed) {
        float font = (float) textSize.getInput();
        double rate = 5.0;
        double increase = (double) elapsed * rate;
        double decrease = -(double) elapsed * rate;
        float cap = 1.0F;

        for (int i = 0; i < this.rows.size(); i++) {
            CompactArrayListRenderer.Row row = this.rows.get(i);
            if (row.enabled) {
                if (row.progress != cap) {
                    row.progress = Math.min(cap, (float) ((double) row.progress + increase));
                }
            } else {
                row.progress = (float) ((double) row.progress + decrease);
                if (!(row.progress > 0.0F)) {
                    this.rows.remove(i);
                }
            }
        }

        float gap = 4.0F;
        if (this.measure) {
            for (CompactArrayListRenderer.Row row : this.rows) {
                float[] bounds = new float[4];
                CanvasFonts.bounds(row.name, 0.0F, 0.0F, font, 9, bounds);
                row.width = bounds[2];
                row.height = bounds[3];
                if (!row.status.isEmpty()) {
                    CanvasFonts.bounds(row.status, 0.0F, 0.0F, font, 9, bounds);
                    row.width = bounds[2] + gap + row.width;
                }
            }

            Collections.sort(
                    this.rows,
                    (a, b) ->
                            b.width > a.width
                                    ? 1
                                    : (a.width > b.width ? -1 : a.name.compareTo(b.name)));
            this.measure = false;
        }

        float y = 0.0F;
        DrawPlan p = new DrawPlan().canvas();

        for (int ix = 0; ix < this.rows.size(); ix++) {
            CompactArrayListRenderer.Row rowx = this.rows.get(ix);
            float alpha =
                    rowx.progress == cap
                            ? cap
                            : (3.0F - (rowx.progress + rowx.progress))
                                    * (rowx.progress * rowx.progress);
            float corner = (float) bgRounding.getInput();
            if (ix + 1 < this.rows.size()) {
                corner = Math.min(rowx.width - this.rows.get(ix + 1).width, corner);
            }

            corner = Math.max(0.0F, corner);
            float px = (float) paddingX.getInput();
            float twiceX = px + px;
            float py = (float) paddingY.getInput();
            float barX = screen - twiceX - rowx.width * alpha;
            float barWidth = twiceX + rowx.width;
            float barHeight = py + py + rowx.height;
            p.color(0.0F, 0.0F, 0.0F, 0.7F * alpha)
                    .rectVarying(barX, y, barWidth, barHeight, 0.0F, 0.0F, 0.0F, corner);
            float textY = py + y;
            float textX = screen - px - rowx.width * alpha;
            float end =
                    p.canvasText(
                            rowx.name, textX, textY, argb(nameColor.getColor(), alpha), font, 9);
            if (!rowx.status.isEmpty()) {
                p.canvasText(
                        rowx.status,
                        end + gap,
                        textY,
                        argb(detailsColor.getColor(), alpha),
                        font,
                        9);
            }

            py = (float) paddingY.getInput();
            y += alpha * (py + py + rowx.height);
        }

        return p;
    }

    private static int argb(int color, float alpha) {
        return (int) (alpha * 255.0F) << 24 | color & 16777215;
    }

    public List<CompactArrayListRenderer.Row> rows() {
        return Collections.unmodifiableList(this.rows);
    }

    public void reset() {
        this.rows.clear();
        this.dirty = true;
        this.lastFrame = 0.0;
    }

    public static final class Row {
        public final Module module;
        public String name;
        public String status;
        public float width;
        public float height;
        public float progress;
        public boolean enabled = true;

        private Row(Module module) {
            this.module = module;
        }
    }
}
