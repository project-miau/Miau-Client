package idle.miau.clickgui.panel;

import idle.miau.Miau;
import idle.miau.clickgui.ClickGuiInput;
import idle.miau.module.Module;
import idle.miau.module.impl.client.ClickGui;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public final class MiauPanelClickGui extends GuiScreen {
   private final PanelGuiState.Runtime state = new PanelGuiState.Runtime();
   private final ProfileServices.Host profiles = new MimeowProfileHost();
   private List<ProfileServices.Record> profileRows = Collections.emptyList();
   private float profileHeight;
   private final Object newProfile = new Object();
   private final Object hexEdit = new Object();
   private final Map<Module, Float> heights = new IdentityHashMap<>();
   private final Map<Module, PanelGuiState.Toggle> toggles = new IdentityHashMap<>();
   private final Map<Module, GuiPresentation.Expansion> expansions = new IdentityHashMap<>();
   private final Map<Module, GuiPresentation.Binding> bindings = new IdentityHashMap<>();
   private final GuiPresentation presentation = new GuiPresentation(CanvasFonts::width, true);
   private final InlineModuleSettings inline;
   private int lastMouseX;
   private int lastMouseY;
   private float lastPartialTicks;
   private final GuiPresentation.Tooltip tooltip = new GuiPresentation.Tooltip();
   private float drawScale;
   private float drawOffsetX;
   private float drawOffsetY;
   private final HoldActivation heldAction = new HoldActivation();
   private final Object unloadAction = new Object();

   public MiauPanelClickGui() {
      this.inline = new InlineModuleSettings(this.presentation, this.tooltip);
   }

   private static float c(int at) {
      return RenderConstants.f(at);
   }

   private static double d(int at) {
      return RenderConstants.d(at);
   }

   public ColorValue accent() {
      if (ClickGui.panelAccent != null) {
         return new ColorValue(
            ClickGui.panelAccent.getRed() / 255.0F,
            ClickGui.panelAccent.getGreen() / 255.0F,
            ClickGui.panelAccent.getBlue() / 255.0F
         );
      }
      return new ColorValue(1.0F, 0.35F, 0.0F);
   }

   public boolean compact() {
      return ClickGui.panelCompact != null && ClickGui.panelCompact.isToggled();
   }

   public boolean wide() {
      return ClickGui.panelWide != null && ClickGui.panelWide.isToggled();
   }

   @Override
   public boolean doesGuiPauseGame() {
      return false;
   }

   @Override
   public void initGui() {
      this.state.start(RenderAccess.now());
      if (Mouse.isGrabbed()) {
         Mouse.setCursorPosition(this.mc.displayWidth / 2, this.mc.displayHeight / 2);
         Mouse.setGrabbed(false);
      }
   }

   @Override
   public void onGuiClosed() {
      this.heldAction.reset();
      this.state.finishModal(true);
      this.state.clearInteraction();
      RenderAccess.releaseCanvasResources();
      if (Miau.configManager != null && Miau.currentConfig != null) {
         Miau.configManager.saveConfig(Miau.currentConfig);
      }
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      this.lastMouseX = mouseX;
      this.lastMouseY = mouseY;
      this.lastPartialTicks = partialTicks;

      int previousMatrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
      try (GlStateScope stateScope = GlStateScope.open()) {
         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glPushMatrix();
         GL11.glLoadIdentity();
         GL11.glOrtho(0.0, (double) this.mc.displayWidth, (double) this.mc.displayHeight, 0.0, -3000.0, 3000.0);

         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPushMatrix();
         GL11.glLoadIdentity();

         GlStateManager.disableDepth();
         GlStateManager.disableAlpha();
         GlStateManager.enableBlend();
         GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);

         float scale = ClickGui.getClickGuiScale();
         float rawMouseX = (float) Mouse.getX();
         float rawMouseY = (float) (this.mc.displayHeight - Mouse.getY() - 1);
         DrawPlan plan = this.framePlan(this.mc.displayWidth, this.mc.displayHeight, scale, rawMouseX, rawMouseY, RenderAccess.now());
         RenderAccess.execute(plan);
      } finally {
         GL11.glMatrixMode(GL11.GL_MODELVIEW);
         GL11.glPopMatrix();
         GL11.glMatrixMode(GL11.GL_PROJECTION);
         GL11.glPopMatrix();
         GL11.glMatrixMode(previousMatrix);
      }
   }

   public DrawPlan framePlan(int displayWidth, int displayHeight, float scale, float mouseX, float mouseY, double now) {
      DrawPlan plan;
      try (CanvasFonts.GuiFaceScope fonts = CanvasFonts.guiFace()) {
         plan = this.buildFramePlan(displayWidth, displayHeight, scale, mouseX, mouseY, now);
      }
      return plan;
   }

   private DrawPlan buildFramePlan(int displayWidth, int displayHeight, float scale, float mouseX, float mouseY, double now) {
      int selected = GuiAppearance.category(GuiAppearance.tab(this.state.selected));
      if (this.state.selected != selected) {
         this.state.selected = selected;
         this.state.selectedAnimation = GuiAppearance.tab(selected);
         this.state.clearInteraction();
      }
      float elapsed = this.state.elapsed(now);
      float previousFade = this.state.fade;
      this.heldAction.beginFrame();
      this.state.rawX = mouseX;
      this.state.rawY = mouseY;
      float w = (float) displayWidth / scale;
      float h = (float) displayHeight / scale;
      RenderMath.Rect panel = this.state.beginFrame(w, h, scale, this.wide(), now);
      float opacity = fade(previousFade);
      this.drawScale = scale;
      this.drawOffsetX = 0.0F;
      this.drawOffsetY = c(1313236) + opacity * c(1313240) - this.state.scroll;
      DrawPlan plan = new DrawPlan()
         .canvas()
         .push()
         .scale(scale, scale, 1.0F)
         .opacity(opacity)
         .color(c(1313228), c(1313228), c(1313228), c(1313232))
         .rect(0.0F, 0.0F, w, h, 0.0F)
         .translate(0.0F, this.drawOffsetY, 0.0F);
      PanelGuiState.Layout layout = this.state.layouts.peek();
      RenderMath.Rect tabs = layout.next(c(1312896) * GuiAppearance.tabRows(layout.rect.width));
      plan.color(c(1312900), c(1312900), c(1312900), 1.0F).rect(tabs.x, tabs.y, tabs.width, tabs.height, c(1312904));
      this.state.selectedAnimation = PanelGuiState.limited(
         this.state.selectedAnimation, (float) GuiAppearance.tab(this.state.selected), (float) ((double) elapsed / d(1312912))
      );
      int from = (int) this.state.selectedAnimation;
      RenderMath.Rect start = GuiAppearance.tabBounds(tabs, from);
      RenderMath.Rect end = GuiAppearance.tabBounds(tabs, Math.min(from + 1, GuiAppearance.TABS.length - 1));
      float progress = this.state.selectedAnimation - from;
      RenderMath.Rect highlight = new RenderMath.Rect(start.x + (end.x - start.x) * progress,
         start.y + (end.y - start.y) * progress, start.width, start.height).inset(c(1312920));
      plan.color(c(1312924), c(1312924), c(1312924), 1.0F).rect(highlight.x, highlight.y, highlight.width, highlight.height, c(1312928));
      String[] labels = GuiAppearance.TABS;

      for (int i = 0; i < labels.length; i++) {
         RenderMath.Rect r = GuiAppearance.tabBounds(tabs, i);
         if (i == labels.length - 1) {
            if (this.heldAction.update(this.unloadAction, this.state.hover(r), this.state.down, this.state.modal == 0, !this.state.consumed, now)) {
               this.state.closeRequested = true;
               this.mc.displayGuiScreen(null);
            }

            float held = this.heldAction.progress(this.unloadAction);
            if (held > 0.0F) {
               plan.color(0.8F, 0.25F, 0.27F, 0.25F).rect(r.x + 6.0F, r.y + 6.0F, (r.width - 12.0F) * held, r.height - 12.0F, 14.0F);
            }
         } else if (this.state.click(r)) {
            this.state.selected = GuiAppearance.category(i);
            this.state.mode = 0;
         }

         RenderMath.Rect inner = r.inset(c(1312920));
         boolean hover = this.state.hover(inner);
         int color = i == labels.length - 1
            ? rgb(c(hover ? 1312948 : 1312936), c(hover ? 1312952 : 1312944), c(hover ? 1312952 : 1312944))
            : this.gray(hover ? 1312940 : 1312936);
         this.presentation.text(plan, labels[i], inner, 1, 0, c(1312932), color);
      }

      float difference = (float) GuiAppearance.tab(this.state.selected) - this.state.selectedAnimation;
      plan.push();
      if (difference != 0.0F) {
         float magnitude = Float.intBitsToFloat(Float.floatToRawIntBits(difference) & RenderConstants.i(1313024));
         float alpha = c(1312976) - (magnitude + magnitude);
         if (magnitude > c(1312972)) {
            alpha = 0.0F;
         }

         this.drawOffsetX = (float) ((double) difference * ((double) w * d(1313040)));
         plan.opacity(alpha).translate(this.drawOffsetX, 0.0F, 0.0F);
      }

      if (this.state.selected != GuiAppearance.PROFILE_PAGE) {
         this.renderCategory(plan, layout, now, elapsed, w, h);
      } else {
         this.renderProfiles(plan, layout, now, w, h);
      }

      plan.unclip().pop();
      this.state.pop();
      int categoryBottom = this.state.contentBottom;
      this.drawOffsetX = 0.0F;
      this.presentation.tooltip(plan, this.tooltip, elapsed);
      plan.unclip().pop();
      if (this.state.modal != 0) {
         float modalOpacity = fade(this.state.modalProgress);
         plan.push().scale(scale, scale, 1.0F).opacity(modalOpacity).color(c(1313228), c(1313228), c(1313228), c(1313232)).rect(0.0F, 0.0F, w, h, 0.0F);
         this.drawOffsetY = c(1313236) + modalOpacity * c(1313240);
         plan.translate(0.0F, this.drawOffsetY, 0.0F);
         this.state.mouseY = this.state.mouseY - this.state.scroll;
         this.state.resetLayout();
         RenderMath.Rect modalPanel = panel;
         if (panel.width > c(1313172)) {
            modalPanel = new RenderMath.Rect((float) ((double) panel.x + (double) (panel.width + c(1313176)) * d(1313080)), panel.y, c(1313172), panel.height);
         }

         PanelGuiState.Layout modalLayout = this.state.push(modalPanel, c(1313016), c(1313020), false);
         this.renderModal(plan, modalLayout, now);
         this.state.pop();
         plan.unclip().pop();
      }

      this.state.contentBottom = categoryBottom;
      this.state.endFrame(h, now);
      this.heldAction.endFrame();
      return plan;
   }

   public void expanded(Module entry, boolean open) {
      GuiPresentation.Expansion expansion = new GuiPresentation.Expansion();
      expansion.open = open;
      this.expansions.put(entry, expansion);
   }

   private static float fade(float progress) {
      return c(1313220) - NumericServices.powf(c(1313220) - progress, c(1313224));
   }

   private static int rgb(float r, float g, float b) {
      return 0xFF000000 | ((int) (r * 255.0F) & 0xFF) << 16 | ((int) (g * 255.0F) & 0xFF) << 8 | (int) (b * 255.0F) & 0xFF;
   }

   private void clip(DrawPlan plan, PanelGuiState.Layout layout) {
      RenderMath.Rect r = layout.clip;
      plan.clip((r.x + this.drawOffsetX) * this.drawScale, (r.y + this.drawOffsetY) * this.drawScale, r.width * this.drawScale, r.height * this.drawScale);
   }

   private void renderCategory(DrawPlan plan, PanelGuiState.Layout layout, double now, float elapsed, float width, float height) {
      ColorValue accent = this.accent();
      float[] accentArray = new float[]{accent.red, accent.green, accent.blue, 1.0F};
      layout.autoColumns(c(1313248));

      for (Module entry : GuiAppearance.entries(this.state.selected)) {
         float old = this.heights.containsKey(entry) ? this.heights.get(entry) : 0.0F;
         RenderMath.Rect outer = layout.next(old);
         if (PanelGuiState.intersects(new RenderMath.Rect(0.0F, this.state.scroll, width, height), outer)) {
            plan.color(c(1313264), c(1313264), c(1313264), 1.0F).rect(outer.x, outer.y, outer.width, outer.height, c(1313268));
            PanelGuiState.Layout inside = this.state.push(outer, c(1313272), 12.0F, true);
            this.clip(plan, inside);
            inside.columns(1);
            RenderMath.Rect title = inside.next(c(1313268));
            GuiPresentation.Expansion expansion = this.expansions.get(entry);
            if (expansion == null) {
               expansion = new GuiPresentation.Expansion();
               this.expansions.put(entry, expansion);
            }

            boolean hasSettings = entry.getSettings() != null && !entry.getSettings().isEmpty();
            float advance;
            if (this.compact()) {
               advance = this.presentation
                  .moduleText(
                     this.state, plan, expansion, hasSettings, entry.getDisplayName(), title, 16.0F, this.gray(1313280), this.gray(1313284)
                  );
            } else {
               advance = this.presentation.text(plan, entry.getDisplayName(), title, 0, 0, c(1313276), this.gray(1313280));
            }

            GuiPresentation.Binding record = this.bindings.get(entry);
            if (record == null) {
               int nativeKey = NativeKeyMap.keyboardId(entry.getKeycode());
               record = new GuiPresentation.Binding(nativeKey, entry.isHoldBind(), entry, key -> {
                  entry.setBind(NativeKeyMap.gameCode(key));
                  Miau.markCurrentConfigUnsaved();
               });
               this.bindings.put(entry, record);
            }

            if (this.state.mode != 1 || this.state.identity != entry) {
               record.key = NativeKeyMap.keyboardId(entry.getKeycode());
               record.hold = entry.isHoldBind();
            }

            this.presentation
               .binding(
                  this.state,
                  plan,
                  record,
                  new RenderMath.Rect(advance + c(1313288), title.y, title.width - advance + title.x, title.height),
                  accentArray,
                  true
               );
            if (entry.isHoldBind() != record.hold) {
               entry.setHoldBind(record.hold);
               Miau.markCurrentConfigUnsaved();
            }

            RenderMath.Rect toggle = new RenderMath.Rect(title.x + title.width + c(1313292), title.y, c(1313296), title.height);
            PanelGuiState.Toggle animation = this.toggles.get(entry);
            if (animation == null) {
               animation = new PanelGuiState.Toggle();
               this.toggles.put(entry, animation);
            }

            boolean enabled = GuiWidgets.toggle(
               this.state, plan, animation, toggle, entry.isEnabled(), accentArray, now
            );
            if (enabled != entry.isEnabled()) {
               entry.toggle();
               Miau.markCurrentConfigUnsaved();
            }

            if (!this.compact()) {
               RenderMath.Rect description = inside.next(c(1313268));
               this.presentation
                  .moduleText(
                     this.state,
                     plan,
                     expansion,
                     hasSettings,
                     GuiAppearance.description(entry),
                     description,
                     14.0F,
                     this.gray(1313300),
                     this.gray(1313304)
                  );
            }

            if (expansion.open) {
               this.inline.render(entry, this.state, plan, inside, accentArray, now);
            }

            float target = GuiPresentation.categoryHeight(this.state.pop());
            this.heights.put(entry, old == 0.0F ? target : PanelGuiState.limited(old, target, (float) ((double) elapsed / d(1313256))));
            plan.unclip();
         }
      }
   }

   private void center(DrawPlan p, String text, RenderMath.Rect r, float font, int color) {
      float measured = CanvasFonts.width(text, 0, font);
      float x = (float) ((double) r.width * d(1312824) + (double) r.x) - measured * 0.5F;
      float y = (float) ((double) r.height * d(1312824) + (double) (r.y + c(1312832)));
      p.canvasText(text, x, y, color, font, 17);
   }

   private int gray(int at) {
      int g = (int) (c(at) * 255.0F);
      return 0xFF000000 | g << 16 | g << 8 | g;
   }

   private void error(Throwable failure) {
      this.state.beginPrompt(failure.getMessage() == null ? failure.toString() : failure.getMessage(), () -> {
      }, RenderAccess.now());
   }

   private void perform(int action, String name, boolean overwrite) {
      try {
         if (action == 0) {
            this.profiles.load(name);
         } else if (action == 1) {
            this.profiles.save(name, overwrite);
         } else if (action == 2) {
            this.profiles.explore();
         } else if (action == 3) {
            this.profiles.delete(name);
         }
      } catch (IOException var5) {
         this.error(var5);
      }
   }

   private void profileAction(int action, String name, double now) {
      if (action == 1 && name == null) {
         this.error(new ProfileServices.Failure(6003, RenderConstants.text(1313671)));
      } else if (action == 3 && name == null) {
         this.error(new ProfileServices.Failure(6004, RenderConstants.text(1313637)));
      } else if (this.state.profileActionsConfirmed) {
         this.perform(action, name, true);
      } else {
         int at = 1313840 + RenderConstants.i(1313840 + action * 4);
         this.state.beginPrompt(RenderConstants.text(at).replace("%s", name == null ? RenderConstants.text(1313482) : name), () -> {
            this.state.profileActionsConfirmed = true;
            this.perform(action, name, true);
         }, now);
      }
   }

   private void renameProfile(ProfileServices.Record record, double now) {
      this.state.beginText("Rename profile", record, PanelGuiState::profileNameFilter, name -> {
         if (name == null) {
            return;
         }
         try {
            this.profiles.rename(record.name, name);
            this.profileRows = this.profiles.records();
            this.state.profileStamp = now;
         } catch (IOException failure) {
            this.error(failure);
         }
      }, now);
      this.state.text.append(record.name);
   }

   private void renderProfiles(DrawPlan plan, PanelGuiState.Layout layout, double now, float width, float height) {
      if (this.state.mode == 0 && now > this.state.profileStamp + d(1313048)) {
         try {
            this.profileRows = this.profiles.records();
         } catch (IOException var21) {
            this.profileRows = this.profiles.snapshot();
            this.error(var21);
         }

         this.state.profileStamp = now;
      }

      layout.autoColumns(c(1313056));
      float radius = c(1313064);
      float font = c(1313068);

      for (int row = 0; row <= this.profileRows.size(); row++) {
         ProfileServices.Record record = row == 0 ? null : this.profileRows.get(row - 1);
         RenderMath.Rect outer = layout.next(this.profileHeight);
         if (PanelGuiState.intersects(new RenderMath.Rect(0.0F, this.state.scroll, width, height), outer)) {
            plan.color(c(1313060), c(1313060), c(1313060), 1.0F).rect(outer.x, outer.y, outer.width, outer.height, radius);
            PanelGuiState.Layout inside = this.state.push(outer, c(1313016), 12.0F, true);
            this.clip(plan, inside);
            inside.columns(1);
            RenderMath.Rect head = inside.next(radius);
            RenderMath.Rect name = new RenderMath.Rect(head.x, head.y, Math.max(0.0F, head.width - 124.0F), head.height);
            boolean nameHovered = record != null && this.state.hover(name);
            this.presentation.text(plan, record == null ? RenderConstants.text(1313482) : record.name,
               name, 0, 0, font, this.gray(nameHovered ? 1312976 : 1313072));
            if (record != null) {
               if (nameHovered) {
                  this.tooltip.request("Rename profile", PanelMath.truncateInt(name.x), PanelMath.truncateInt(name.y));
               }
               if (this.state.click(name)) {
                  this.renameProfile(record, now);
               }
            }

            for (int slot = 0; slot < 4; slot++) {
               RenderMath.Rect button = new RenderMath.Rect(head.x + head.width - (float) (80 - 20 * slot) - (float) (36 - 12 * slot), head.y, radius, 20.0F);
               boolean enabled = (slot & (record == null ? 1 : 0)) == 0;
               int at = 1313492 + RenderConstants.i(1313492 + slot * 4);
               String label = RenderConstants.text(at);
               ProfileIcons.draw(plan, button, slot, enabled, enabled && this.state.hover(button));
               if (enabled && this.state.hover(button)) {
                  this.tooltip
                     .request(label, PanelMath.truncateInt((double) button.x + (double) button.width * d(1313080)), PanelMath.truncateInt(button.y));
               }

               if (enabled && this.state.click(button)) {
                  this.profileAction(slot, record == null ? null : record.name, now);
               }
            }

            if (!this.compact()) {
               RenderMath.Rect date = inside.next(radius);
               this.presentation.text(plan, record == null ? RenderConstants.text(1313536) : record.date, date, 0, 0, c(1313088), this.gray(1313092));
            }

            this.profileHeight = this.state.pop();
            plan.unclip();
         }
      }

      RenderMath.Rect create = layout.next(this.profileHeight);
      plan.color(c(1313060), c(1313060), c(1313060), c(1312972)).rect(create.x, create.y, create.width, create.height, radius);
      if (this.state.click(create)) {
         this.state.activateText(this.newProfile, PanelGuiState::profileNameFilter);
      }

      if (this.state.textActive(this.newProfile)) {
         RenderMath.Rect input = new RenderMath.Rect(
            create.x, (float) ((double) create.y + (double) create.height * d(1313080) + d(1313104)), create.width, c(1313124)
         );
         this.presentation.textEntry(this.state, plan, input, 1, font, now);
         String name = this.state.takeText();
         if (name != null) {
            this.perform(1, name, false);
         }
      } else {
         this.center(plan, RenderConstants.text(1313821), create, c(1313096), this.gray(this.state.hover(create) ? 1312976 : 1313072));
      }
   }

   private void renderModal(DrawPlan plan, PanelGuiState.Layout parent, double now) {
      this.state.modalDrawing = true;
      this.state.modalProgress = RenderMath.min(c(1314448), (float) ((double) this.state.modalProgress + (now - this.state.modalStamp) / d(1314440)));
      parent.columns(1);
      RenderMath.Rect outer = parent.next(this.state.modalHeight);
      plan.color(c(1314452), c(1314452), c(1314452), 1.0F).rect(outer.x, outer.y, outer.width, outer.height, c(1314456));
      PanelGuiState.Layout inner = this.state.push(outer, c(1314460), 12.0F, true);
      this.clip(plan, inner);
      inner.columns(1);
      RenderMath.Rect heading = inner.next(c(1314456));
      this.presentation.text(plan, this.state.title, heading, 0, 0, c(1314464), this.gray(1314468));
      RenderMath.Rect close = new RenderMath.Rect(heading.width - heading.height + heading.x, heading.y, heading.height, heading.height);
      float inset = (float) ((double) close.width * d(1314488));
      boolean hover = this.state.hover(close);
      plan.color(c(hover ? 1314476 : 1314472), c(hover ? 1314480 : 1314472), c(hover ? 1314480 : 1314472), 1.0F)
         .lineWidth(c(1314484))
         .line(close.x + inset, close.y + inset, close.x + close.width - inset, close.y + close.height - inset)
         .line(close.x + close.width - inset, close.y + inset, close.x + inset, close.y + close.height - inset);
      boolean closePressed = this.state.click(close);
      int mode = this.state.modal;
      if (mode == 1 || mode == 4) {
         RenderMath.Rect input = inner.next(c(1314496));
         plan.color(c(1314504), c(1314504), c(1314504), 1.0F).lineWidth(c(1314484)).roundedStroke(input.x, input.y, input.width, input.height, c(1314460));
         if (!this.state.textActive(this.state.identity)) {
            this.state.finishModal(true);
         } else {
            this.presentation.textEntry(this.state, plan, input.inset(c(1314508)), 1, c(1314500), now);
            if (mode == 1) {
               this.state.submitModalText();
            } else {
               this.state.filterOptions(now);
               RenderMath.Rect pair = null;
               Integer chosen = null;

               for (int i = 0; i < this.state.matches.size(); i++) {
                  if ((i & 1) == 0) {
                     pair = inner.next(c(1314520));
                  }

                  RenderMath.Rect button = new RenderMath.Rect(pair.x + (float) (i & 1) * pair.width * c(1314524), pair.y, pair.width * c(1314524), pair.height);
                  int index = this.state.matches.get(i);
                  this.presentation
                     .text(plan, this.state.options.get(index), button, 0, 0, c(1314464), this.gray(this.state.hover(button) ? 1314448 : 1314468));
                  if (this.state.click(button)) {
                     chosen = index;
                  }
               }

               if (this.state.overflow != 0) {
                  RenderMath.Rect note = inner.next(c(1314520));
                  this.presentation
                     .text(
                        plan,
                        RenderConstants.text(1314705).replace("%u", Integer.toString(Math.min(this.state.overflow, 9999))),
                        note,
                        0,
                        0,
                        c(1314500),
                        this.gray(1314472)
                     );
               }

               if (chosen != null) {
                  this.state.choose(chosen);
               }
            }
         }
      } else if (mode == 3) {
         RenderMath.Rect prompt = inner.next(c(1314496));
         this.presentation.text(plan, this.state.prompt, prompt, 0, 0, c(1314500), this.gray(1314468));
         RenderMath.Rect button = inner.next(c(1314496));
         boolean over = this.state.hover(button);
         plan.color(c(over ? 1314476 : 1314468), c(over ? 1314480 : 1314528), c(over ? 1314480 : 1314528), 1.0F)
            .rect(button.x, button.y, button.width, button.height, c(1314532));
         this.center(plan, RenderConstants.text(1314696), button, c(1314500), this.gray(1314448));
         if (this.state.click(button)) {
            this.state.confirmPrompt();
         }
      } else if (mode == 2) {
         this.renderColor(plan, inner);
      }

      this.state.modalHeight = this.state.pop();
      plan.unclip();
      this.state.modalDrawing = false;
      this.state.modalStamp = now;
      if (closePressed && this.state.modal != 0) {
         this.state.finishModal(true);
      }
   }

   private static float[] sample(float[][] stops, float t) {
      if (stops.length == 1) {
         return stops[0];
      } else {
         float position = RenderMath.clamp(0.0F, 1.0F, t) * (float) (stops.length - 1);
         int index = Math.min(stops.length - 2, (int) position);
         float f = position - (float) index;
         float[] color = new float[4];

         for (int c = 0; c < 4; c++) {
            color[c] = stops[index][c] + (stops[index + 1][c] - stops[index][c]) * f;
         }

         return color;
      }
   }

   private void renderColor(DrawPlan plan, PanelGuiState.Layout layout) {
      RenderMath.Rect row = layout.next(c(1314536));
      plan.color(this.state.rgb[0], this.state.rgb[1], this.state.rgb[2], 1.0F).rect(row.x, row.y, row.height, row.height, c(1314532));
      plan.color(0.33F, 0.33F, 0.33F, 1.0F).lineWidth(1.0F).roundedStroke(row.x, row.y, row.height, row.height, c(1314532));
      String hex = String.format(
         Locale.ROOT,
         "%02x%02x%02x",
         (int) (PanelMath.truncateLong(this.state.rgb[0] * c(1314540)) & 255L),
         (int) (PanelMath.truncateLong(this.state.rgb[1] * c(1314540)) & 255L),
         (int) (PanelMath.truncateLong(this.state.rgb[2] * c(1314540)) & 255L)
      );
      plan.canvasText(
         RenderConstants.text(1314681) + (this.state.textActive(this.hexEdit) ? this.state.text.toString() : hex),
         row.x + row.height + c(1314460),
         row.y,
         this.gray(1314472),
         c(1314456),
         9
      );
      if (this.state.click(row)) {
         this.state.activateText(this.hexEdit, PanelGuiState::hexFilter);
      }

      if (this.state.textActive(this.hexEdit)) {
         String value = this.state.takeText();
         if (value != null) {
            this.state.hex(value);
         }
      }

      for (int component = 0; component < 3; component++) {
         RenderMath.Rect label = layout.next(19.0F);
         plan.canvasText(
            RenderConstants.text(component == 0 ? 1314642 : (component == 1 ? 1314646 : 1314657)), label.x, label.y + 9.0F, this.gray(1314468), c(1314500), 9
         );
         RenderMath.Rect slider = layout.next(c(1314500));
         float edge = 1.0F;
         RenderMath.Rect track = new RenderMath.Rect(slider.x + edge, slider.y, slider.width - 2.0F, slider.height);
         float[][] stops;
         if (component == 0) {
            int steps = (int) c(1314560);
            stops = new float[steps + 1][];

            for (int i = 0; i <= steps; i++) {
               stops[i] = PanelGuiState.hsl((float) i / (float) steps, this.state.hsl[1], this.state.hsl[2], 255);
            }
         } else {
            stops = new float[][]{
               PanelGuiState.hsl(this.state.hsl[0], component == 1 ? 0.0F : this.state.hsl[1], component == 2 ? 0.0F : this.state.hsl[2], 255),
               PanelGuiState.hsl(this.state.hsl[0], component == 1 ? c(1314448) : this.state.hsl[1], component == 2 ? c(1314448) : this.state.hsl[2], 255)
            };
         }

         plan.pillGradient(track, 5.0F, stops);
         plan.color(0.2509804F, 0.2509804F, 0.2509804F, 1.0F).lineWidth(2.0F).roundedStroke(track.x, track.y, track.width, track.height, 5.0F);
         float outer = 10.0F + edge;
         float px = (slider.width - 2.0F * outer) * this.state.hsl[component] + slider.x + outer;
         float py = (float) ((double) slider.height * d(1314616) + (double) slider.y);
         float[] under = sample(stops, this.state.hsl[component]);
         plan.color(under[0], under[1], under[2], 1.0F).op("circle", px, py, 10.0F);
         plan.color(0.2509804F, 0.2509804F, 0.2509804F, 1.0F).lineWidth(2.0F).circleStroke(px, py, 10.0F);
         this.state.colorSlider(slider, component, outer);
      }

      RenderMath.Rect label = layout.next(19.0F);
      plan.canvasText(RenderConstants.text(1314667), label.x, label.y + 9.0F, this.gray(1314468), c(1314500), 9);
      row = layout.next(c(1314520));
      float x = row.x;
      float width = (row.width + c(1314564)) * c(1314568);
      float spacing = c(1314460) + width;

      for (int back = 8; back > 0; back--) {
         int index = this.state.historyIndex >= back ? this.state.historyIndex - back : this.state.historyIndex + 8 - back;
         float[] color = this.state.history[index];
         RenderMath.Rect button = new RenderMath.Rect(x, row.y, width, row.height);
         plan.color(color[0], color[1], color[2], 1.0F).rect(button.x, button.y, button.width, button.height, 5.0F);
         boolean over = this.state.hover(button);
         float edge = over ? 0.55F : 0.33F;
         plan.color(edge, edge, edge, 1.0F).lineWidth(1.0F).roundedStroke(button.x, button.y, button.width, button.height, 5.0F);
         if (this.state.click(button)) {
            this.state.selectHistory(index);
         }

         x += spacing;
      }
   }

   @Override
   public void handleMouseInput() throws IOException {
      int x = Mouse.getEventX();
      int y = this.mc.displayHeight - Mouse.getEventY() - 1;
      this.state.rawX = (float) x;
      this.state.rawY = (float) y;
      int button = Mouse.getEventButton();
      if (button >= 0) {
         boolean pressed = Mouse.getEventButtonState();
         int nativeKey = NativeKeyMap.mouseId(button);
         this.state.input(2, nativeKey, x, y, 0.0F, pressed, new byte[0]);
      } else if (Mouse.getEventDWheel() != 0) {
         this.scroll(Mouse.getEventDWheel());
      } else {
         this.state.input(1, 0, x, y, 0.0F, false, new byte[0]);
      }
      super.handleMouseInput();
   }

   void scroll(int wheelDelta) {
      this.state.input(3, 0, 0, 0, ClickGuiInput.scrollSteps(wheelDelta), false, new byte[0]);
   }

   @Override
   public void handleKeyboardInput() throws IOException {
      int key = Keyboard.getEventKey();
      char character = Keyboard.getEventCharacter();
      boolean pressed = Keyboard.getEventKeyState();
      int nativeKey = NativeKeyMap.keyboardId(key);
      byte[] utf8 = Character.toString(character).getBytes(StandardCharsets.UTF_8);
      if (pressed) {
         if (!this.state.input(2, nativeKey, 0, 0, 0.0F, true, utf8)) {
            if (key == 1) {
               if (this.state.modal != 0) {
                  this.state.finishModal(true);
               } else {
                  this.mc.displayGuiScreen(null);
               }
            }
         }
      } else {
         this.state.input(2, nativeKey, 0, 0, 0.0F, false, new byte[0]);
      }
      super.handleKeyboardInput();
   }

   @Override
   protected void keyTyped(char character, int key) throws IOException {
      if (key == 1) {
         if (this.state.modal != 0) {
            this.state.finishModal(true);
         } else {
            this.mc.displayGuiScreen(null);
         }
      } else {
         super.keyTyped(character, key);
      }
   }
}
