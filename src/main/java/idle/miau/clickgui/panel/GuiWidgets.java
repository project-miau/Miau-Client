package idle.miau.clickgui.panel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final strictfp class GuiWidgets {
   private final GuiPresentation view;
   private final GuiPresentation.Tooltip tooltip;
   private final GuiWidgets.Formatter formatter;

   public GuiWidgets(GuiPresentation view, GuiPresentation.Tooltip tooltip, GuiWidgets.Formatter formatter) {
      this.view = Objects.requireNonNull(view);
      this.tooltip = Objects.requireNonNull(tooltip);
      this.formatter = Objects.requireNonNull(formatter);
   }

   private static strictfp float c(int at) {
      return RenderConstants.f(at);
   }

   private static strictfp double d(int at) {
      return RenderConstants.d(at);
   }

   private static strictfp int gray(float value) {
      int v = (int)(value * 255.0F);
      return 0xFF000000 | (v & 0xFF) << 16 | (v & 0xFF) << 8 | v & 0xFF;
   }

   private static strictfp int rgba(float[] color) {
      return (int)(color[3] * 255.0F) << 24
         | ((int)(color[0] * 255.0F) & 0xFF) << 16
         | ((int)(color[1] * 255.0F) & 0xFF) << 8
         | (int)(color[2] * 255.0F) & 0xFF;
   }

   private static strictfp RenderMath.Rect height(RenderMath.Rect rect, float h) {
      return new RenderMath.Rect(rect.x, rect.y, rect.width, h);
   }

   public strictfp void label(DrawPlan plan, String text, RenderMath.Rect rect, int shade, int size) {
      this.view.text(plan, text, rect, 0, 0, c(size), gray(c(shade)));
   }

   private static strictfp void solid(DrawPlan plan, float[] color) {
      plan.color(color[0], color[1], color[2], color[3]);
   }

   private static strictfp void stroke(DrawPlan plan, RenderMath.Rect rect, int shade, int width, int radius) {
      float v = c(shade);
      plan.color(v, v, v, 1.0F).lineWidth(c(width)).roundedStroke(rect.x, rect.y, rect.width, rect.height, c(radius));
   }

   public static strictfp float[] blend(float[] a, float[] b, float fraction) {
      float amount = fraction < 0.0F ? 0.0F : RenderMath.min(c(2838932), fraction);
      float complement = c(2838932) - amount;
      float[] out = new float[4];

      for (int i = 0; i < 4; i++) {
         out[i] = complement * a[i] + amount * b[i];
      }

      return out;
   }

   private static strictfp void circle(DrawPlan plan, float x, float y, float radius) {
      plan.op("circle", x, y, radius);
   }

   private static strictfp void close(DrawPlan plan, RenderMath.Rect rect, boolean hover, float normal, int red, int other, int inset) {
      float r = hover ? c(red) : normal;
      float g = hover ? c(other) : normal;
      float amount = (float)((double)rect.height * d(inset));
      float left = rect.x + amount;
      float top = rect.y + amount;
      float right = rect.height + rect.x - amount;
      float bottom = rect.y + rect.height - amount;
      plan.color(r, g, g, 1.0F).line(left, top, right, bottom).line(right, top, left, bottom);
   }

   private strictfp String formatted(String format, int defaultAddress, float scale, float... values) {
      double[] args = new double[values.length];

      for (int i = 0; i < args.length; i++) {
         args[i] = (double)(scale != 0.0F ? scale * values[i] : values[i]);
      }

      return this.formatter.format(format == null ? RenderConstants.text(defaultAddress) : format, args);
   }

   private strictfp void tip(String text, float x, float y) {
      this.tooltip.request(text, PanelMath.truncateInt(x), PanelMath.truncateInt(y));
   }

   public static strictfp boolean toggle(
      PanelGuiState.Runtime state, DrawPlan plan, PanelGuiState.Toggle animation, RenderMath.Rect rectangle, boolean value, float[] accent, double now
   ) {
      animation.animate(value, now);
      float amount = animation.smooth();
      float shade = c(state.hover(rectangle) ? 1313880 : 1313876);
      float[] color = blend(new float[]{shade, shade, shade, 1.0F}, accent, amount);
      solid(plan, color);
      plan.rect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, c(1313884));
      if (state.click(rectangle)) {
         value = !value;
      }

      RenderMath.Rect inset = rectangle.inset(c(1313888));
      float white = c(1313892);
      float integerHeight = (float)PanelMath.truncateInt(inset.height);
      float half = integerHeight * c(1313896);
      float x = RenderMath.lerp(half + inset.x, inset.x + inset.width - half, amount);
      float y = half + inset.y;
      plan.color(white, white, white, 1.0F);
      circle(plan, x, y, integerHeight);
      return value;
   }

   public strictfp boolean checkbox(
      PanelGuiState.Runtime state,
      DrawPlan plan,
      PanelGuiState.Layout layout,
      String text,
      PanelGuiState.Toggle animation,
      boolean value,
      float[] accent,
      double now
   ) {
      RenderMath.Rect rect = layout.next(c(1313900));
      this.label(plan, text, rect, 1313904, 1313908);
      return toggle(state, plan, animation, new RenderMath.Rect(rect.x + rect.width + c(1313912), rect.y, c(1313916), rect.height), value, accent, now);
   }

   public strictfp void section(DrawPlan plan, PanelGuiState.Layout layout, String text) {
      RenderMath.Rect rect = layout.next(c(1313920));
      this.label(plan, text, new RenderMath.Rect(rect.x, rect.y + c(1313924), rect.width, rect.height), 1313928, 1313932);
   }

   public strictfp boolean group(PanelGuiState.Runtime state, DrawPlan plan, PanelGuiState.Layout layout,
                                 String text, boolean opened) {
      RenderMath.Rect rect = layout.next(c(1313920));
      this.label(plan, text, new RenderMath.Rect(rect.x, rect.y + c(1313924), rect.width - 12, rect.height), 1313928, 1313932);
      float shade = c(state.hover(rect) ? 1313904 : 1313928);
      float x = rect.x + rect.width - 6;
      float y = rect.y + rect.height * 0.5F + c(1313924);
      plan.color(shade, shade, shade, 1.0F).lineWidth(1.0F);
      if (opened) {
         plan.line(x - 3, y - 1.5F, x, y + 1.5F).line(x, y + 1.5F, x + 3, y - 1.5F);
      } else {
         plan.line(x - 1.5F, y - 3, x + 1.5F, y).line(x + 1.5F, y, x - 1.5F, y + 3);
      }
      return state.click(rect) ? !opened : opened;
   }

   public strictfp float number(
      PanelGuiState.Runtime state,
      DrawPlan plan,
      PanelGuiState.Layout layout,
      String text,
      String format,
      Object id,
      PanelGuiState.Slider animation,
      float minimum,
      float maximum,
      float step,
      float displayScale,
      float value,
      float[] accent,
      double now
   ) {
      RenderMath.Rect rect = layout.next(c(1313936));
      this.label(plan, text, rect, 1313940, 1313944);
      double half = (double)rect.width * d(1313952);
      RenderMath.Rect track = new RenderMath.Rect((float)((double)rect.x + half), rect.y + c(1313948) + c(1313960), (float)(half + d(1313968)), 4.0F);
      float shade = c(state.hover(rect) ? 1313980 : 1313976);
      float radius = c(1313984);
      plan.color(shade, shade, shade, 1.0F).rect(track.x, track.y, track.width, track.height, radius);
      float fraction = maximum > minimum
         ? RenderMath.clamp(0.0F, c(1313988), (value - minimum) / (maximum - minimum)) : 0.0F;
      float ratio = (float)((double)((float)(now - animation.stamp)) / d(1313992));
      animation.animation = PanelGuiState.limited(animation.animation, fraction, ratio);
      animation.stamp = now;
      float end = RenderMath.lerp(track.x, track.x + track.width, animation.animation);
      solid(plan, accent);
      plan.rect(track.x, track.y, end - track.x, 4.0F, radius);
      circle(plan, end, radius + track.y, c(1314000));
      value = animation.action(state, id, track, rect, value, minimum, maximum, step, displayScale);
      if (animation.active || state.hover(rect) && !state.down) {
         this.tip(state.textActive(id) ? state.text.toString() : this.formatted(format, 1314387, displayScale, value), end, track.y);
      }

      return value;
   }

   public void text(
      PanelGuiState.Runtime state,
      DrawPlan plan,
      PanelGuiState.Layout layout,
      String label,
      String value,
      String initialValue,
      Object identity,
      int maxLength,
      Consumer<String> accept,
      double now
   ) {
      RenderMath.Rect rect = layout.next(28.0F);
      this.label(plan, label, height(rect, 20.0F), 1314120, 1314124);
      RenderMath.Rect input = new RenderMath.Rect(rect.x + rect.width * 0.5F, rect.y, rect.width * 0.5F, 20.0F);
      stroke(plan, input, 1314140, 1314144, 1314148);
      this.view.text(plan, value, input, 1, 0, c(1314124), gray(c(state.hover(input) ? 1314168 : 1314120)));
      if (state.click(input)) {
         state.beginText(label, identity, (buffer, character) -> {
            if (buffer.length() + Character.charCount(character) <= maxLength && character >= 32 && character != 127) {
               buffer.appendCodePoint(character);
            }
         }, accept, now);
         state.text.append(initialValue);
      }
   }

   public strictfp float[] range(
      PanelGuiState.Runtime state,
      DrawPlan plan,
      PanelGuiState.Layout layout,
      String text,
      String format,
      String pairFormat,
      Object lowId,
      Object highId,
      GuiWidgets.Range animation,
      float minimum,
      float maximum,
      float step,
      float displayScale,
      float lower,
      float upper,
      float[] accent,
      double now
   ) {
      RenderMath.Rect rect = layout.next(c(1314024));
      this.label(plan, text, rect, 1314028, 1314032);
      double half = (double)rect.width * d(1314040);
      RenderMath.Rect track = new RenderMath.Rect((float)((double)rect.x + half), rect.y + c(1314036) + c(1314048), (float)(half + d(1314056)), 4.0F);
      float shade = c(state.hover(rect) ? 1314068 : 1314064);
      float radius = c(1314072);
      plan.color(shade, shade, shade, 1.0F).rect(track.x, track.y, track.width, 4.0F, radius);
      float ratio = (float)((double)((float)(now - animation.stamp)) / d(1314080));
      float difference = maximum - minimum;
      animation.lower.animation = PanelGuiState.limited(animation.lower.animation, RenderMath.clamp(0.0F, c(1314076), (lower - minimum) / difference), ratio);
      animation.upper.animation = PanelGuiState.limited(animation.upper.animation, RenderMath.clamp(0.0F, c(1314076), (upper - minimum) / difference), ratio);
      animation.stamp = now;
      float lowX = RenderMath.lerp(track.x, track.x + track.width, animation.lower.animation);
      float highX = RenderMath.lerp(track.x, track.x + track.width, animation.upper.animation);
      solid(plan, accent);
      plan.rect(lowX, track.y, highX - lowX, 4.0F, radius);
      circle(plan, lowX, radius + track.y, c(1314088));
      circle(plan, highX, radius + track.y, c(1314088));
      RenderMath.Rect[] hits = rangeHits(track, rect, lowX, highX);
      lower = animation.lower.action(state, lowId, track, hits[0], lower, minimum, maximum, step, displayScale);
      if (animation.lower.active) {
         if (lower > upper) {
            upper = lower;
         }

         this.tip(state.textActive(lowId) ? state.text.toString() : this.formatted(format, 1314387, displayScale, lower), lowX, track.y);
      } else {
         upper = animation.upper.action(state, highId, track, hits[1], upper, minimum, maximum, step, displayScale);
         if (animation.upper.active) {
            if (lower > upper) {
               lower = upper;
            }

            this.tip(state.textActive(highId) ? state.text.toString() : this.formatted(format, 1314387, displayScale, upper), highX, track.y);
         } else if (state.hover(rect) && (!state.down || animation.lower.active || animation.upper.active)) {
            float sum = hits[0].x + hits[0].width + hits[1].x;
            this.tooltip
               .request(
                  this.formatted(pairFormat, 1314348, displayScale, lower, upper),
                  PanelMath.truncateInt((double)sum * d(1314040)),
                  PanelMath.truncateInt(track.y)
               );
         }
      }

      return new float[]{lower, upper};
   }

   public static strictfp RenderMath.Rect[] rangeHits(RenderMath.Rect track, RenderMath.Rect row, float lowX, float highX) {
      float center = (float)PanelMath.truncateInt((double)(lowX + highX) * d(1314040));
      float distance = center - track.x;
      float extra = c(1314096);
      return new RenderMath.Rect[]{
         new RenderMath.Rect(c(1314092) + track.x, row.y, distance + extra, row.height),
         new RenderMath.Rect(center, row.y, track.width - distance + extra, row.height)
      };
   }

   public strictfp int choice(
      PanelGuiState.Runtime state,
      DrawPlan plan,
      PanelGuiState.Layout layout,
      String text,
      GuiWidgets.Choice animation,
      List<String> options,
      int[] current,
      int flag,
      float[] accent,
      double now
   ) {
      int count = options.size();
      float target = animation.open ? (float)Integer.toUnsignedLong(count * 28) : c(1314104);
      float ratio = (float)((double)((float)(now - animation.stamp)) / d(1314112));
      animation.height = PanelGuiState.limited(animation.height, target, ratio);
      animation.stamp = now;
      RenderMath.Rect rect = layout.next(animation.height);
      this.label(plan, text, height(rect, 20.0F), 1314120, 1314124);
      RenderMath.Rect selection = new RenderMath.Rect((float)((double)rect.width * d(1314128) + (double)rect.x), rect.y, rect.width * c(1314136), rect.height);
      stroke(plan, selection, 1314140, 1314144, 1314148);
      float shade = c(1314140);
      if (animation.open) {
         if (count == 0) {
            return 0;
         } else {
            float rowHeight = selection.height / (float)Integer.toUnsignedLong(count);
            boolean selected = false;

            for (int index = 0; index < count; index++) {
               RenderMath.Rect row = new RenderMath.Rect(selection.x, (float)index * rowHeight + selection.y, selection.width, rowHeight);
               boolean hover = state.hover(row);
               int inverted = (flag ^ 1) & 0xFF;
               float[] color;
               if (index == current[0]) {
                  color = accent;
               } else {
                  int at = hover ? (((index == 0 ? 1 : 0) | inverted) != 0 ? 1314168 : 1314176) : (((index != 0 ? 1 : 0) | inverted) != 0 ? 1314120 : 1314172);
                  float value = c(at);
                  color = new float[]{value, value, value, 1.0F};
               }

               this.view.text(plan, options.get(index), row, 1, 0, c(1314124), rgba(color));
               if (!selected && state.click(row)) {
                  current[0] = index;
                  animation.open = false;
                  selected = true;
               }

               if (index != 0) {
                  plan.color(shade, shade, shade, c(1314136)).line(selection.x, row.y, selection.x + selection.width, row.y);
               }
            }

            return selected ? 1 : 0;
         }
      } else {
         boolean hoverx = state.hover(selection);
         if (((flag ^ 1 | (count < 6 ? 1 : 0)) & 0xFF) == 0 && current[0] != 0) {
            RenderMath.Rect clear = new RenderMath.Rect(selection.width - selection.height + selection.x, selection.y, selection.height, selection.height);
            boolean clearHover = state.hover(clear);
            close(plan, clear, clearHover, shade, 1314152, 1314156, 1314160);
            if (state.click(clear)) {
               current[0] = 0;
            }

            hoverx = hoverx && !clearHover;
         }

         int at = flag != 0 && current[0] == 0 ? (hoverx ? 1314176 : 1314172) : (hoverx ? 1314168 : 1314120);
         this.view.text(plan, options.get(current[0]), selection, 1, 0, c(1314124), gray(c(at)));
         if (state.click(selection)) {
            animation.open = true;
         }

         return 0;
      }
   }

   public boolean multiChoice(PanelGuiState.Runtime state, DrawPlan plan, PanelGuiState.Layout layout,
                              String text, Choice animation, List<String> options, Predicate<String> selected,
                              Consumer<String> toggle, float[] accent, double now) {
      float rowHeight = c(1314104);
      float target = rowHeight * (animation.open ? options.size() + 1 : 1);
      float ratio = (float)((now - animation.stamp) / d(1314112));
      animation.height = PanelGuiState.limited(animation.height, target, ratio);
      animation.stamp = now;
      RenderMath.Rect rect = layout.next(animation.height);
      this.label(plan, text, height(rect, 20.0F), 1314120, 1314124);
      RenderMath.Rect selection = new RenderMath.Rect((float)(rect.width * d(1314128) + rect.x),
         rect.y, rect.width * c(1314136), Math.max(rowHeight, rect.height));
      RenderMath.Rect header = height(selection, rowHeight);
      stroke(plan, selection, 1314140, 1314144, 1314148);
      int count = 0;
      String summary = "None";
      for (String option : options) {
         if (selected.test(option)) {
            summary = option.replace('_', ' ');
            count++;
         }
      }
      if (count > 1) summary = count + " selected";
      this.view.text(plan, summary, new RenderMath.Rect(header.x, header.y, header.width - 16.0F, header.height),
         1, 0, c(1314124), gray(c(state.hover(header) ? 1314168 : 1314120)));
      float x = header.x + header.width - 9.0F;
      float y = header.y + rowHeight * 0.5F;
      float shade = c(1314140);
      plan.color(shade, shade, shade, 1.0F).lineWidth(1.0F);
      if (animation.open) {
         plan.line(x - 3.0F, y - 1.5F, x, y + 1.5F).line(x, y + 1.5F, x + 3.0F, y - 1.5F);
      } else {
         plan.line(x - 1.5F, y - 3.0F, x + 1.5F, y).line(x + 1.5F, y, x - 1.5F, y + 3.0F);
      }
      if (state.click(header)) {
         animation.open = !animation.open;
         return false;
      }
      if (!animation.open) return false;
      boolean changed = false;
      for (int index = 0; index < options.size(); index++) {
         RenderMath.Rect row = new RenderMath.Rect(selection.x, selection.y + rowHeight * (index + 1), selection.width, rowHeight);
         if (row.y + row.height > selection.y + selection.height) break;
         String option = options.get(index);
         float value = c(state.hover(row) ? 1314168 : 1314120);
         float[] color = selected.test(option) ? accent : new float[]{value, value, value, 1.0F};
         this.view.text(plan, option.replace('_', ' '), new RenderMath.Rect(row.x, row.y, row.width - 16.0F, row.height),
            1, 0, c(1314124), rgba(color));
         if (selected.test(option)) {
            solid(plan, accent);
            circle(plan, row.x + row.width - 9.0F, row.y + row.height * 0.5F, 3.0F);
         }
         plan.color(shade, shade, shade, c(1314136)).line(row.x, row.y, row.x + row.width, row.y);
         if (state.click(row)) {
            toggle.accept(option);
            changed = true;
         }
      }
      return changed;
   }

   public void itemChoice(PanelGuiState.Runtime state, DrawPlan plan, PanelGuiState.Layout layout,
                          String name, String selected, List<String> choices, Consumer<Integer> accept, double now) {
      RenderMath.Rect rect = layout.next(c(1314104));
      this.label(plan, name, height(rect, 20.0F), 1314120, 1314124);
      RenderMath.Rect selection = new RenderMath.Rect((float) (rect.width * d(1314128) + rect.x),
         rect.y, rect.width * c(1314136), rect.height);
      stroke(plan, selection, 1314140, 1314144, 1314148);
      this.view.text(plan, selected, selection, 1, 0, c(1314124), gray(c(state.hover(selection) ? 1314168 : 1314120)));
      if (state.click(selection)) state.beginOptions(name, choices, accept, now);
   }

   public strictfp boolean color(
      PanelGuiState.Runtime state, DrawPlan plan, PanelGuiState.Layout layout, String text, float[] current, Consumer<float[]> accept, double now
   ) {
      RenderMath.Rect rect = layout.next(c(1314184));
      this.label(plan, text, height(rect, 20.0F), 1314188, 1314192);
      RenderMath.Rect preview = new RenderMath.Rect(rect.x + rect.width + c(1314196), rect.y, c(1314200), 20.0F);
      float radius = preview.height * 0.5F;
      plan.color(current[0], current[1], current[2], 1.0F).rect(preview.x, preview.y, preview.width, preview.height, radius);
      plan.color(0.33F, 0.33F, 0.33F, 1.0F).lineWidth(1.0F).roundedStroke(preview.x, preview.y, preview.width, preview.height, radius);
      RenderMath.Rect hit = new RenderMath.Rect((float)((double)rect.width * d(1314216) + (double)rect.x), rect.y, rect.width * c(1314224), rect.height);
      boolean clicked = state.click(hit);
      if (clicked) {
         state.beginColor(text, current, accept, now);
      }

      return clicked;
   }

   public strictfp boolean list(
      PanelGuiState.Runtime state,
      DrawPlan plan,
      PanelGuiState.Layout layout,
      String text,
      String title,
      PanelGuiState.Filter filter,
      int kind,
      GuiWidgets.ListData list,
      List<String> suggestions,
      double now
   ) {
      long count = list.size();
      long rows = count + 1L;
      float rowCount = GuiPresentation.unsignedFloat(rows);
      RenderMath.Rect rect = layout.next(GuiPresentation.unsignedFloat(rows * 28L));
      this.label(plan, text, height(rect, 20.0F), 1314232, 1314236);
      RenderMath.Rect selection = new RenderMath.Rect((float)((double)rect.width * d(1314240) + (double)rect.x), rect.y, rect.width * c(1314248), rect.height);
      stroke(plan, selection, 1314252, 1314256, 1314260);
      float rowHeight = selection.height / rowCount;
      float shade = c(1314252);
      long removed = -1L;
      boolean result = false;

      for (long index = 0L; Long.compareUnsigned(index, count) < 0; index++) {
         RenderMath.Rect row = new RenderMath.Rect(selection.x, GuiPresentation.unsignedFloat(index) * rowHeight + selection.y, selection.width, rowHeight);
         String name = list.name(index);
         byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
         String shown = new String(bytes, 0, Math.min(bytes.length, 20), StandardCharsets.UTF_8);
         this.view.text(plan, shown, row, 1, 0, c(1314236), gray(c(1314232)));
         if (kind == 1) {
            RenderMath.Rect preview = new RenderMath.Rect(row.x, row.y, row.height, row.height).inset(c(1314264));
            float[] rgb = list.color(index);
            plan.color(rgb[0], rgb[1], rgb[2], 1.0F).rect(preview.x, preview.y, preview.width, preview.height, c(1314268));
            if (state.click(preview)) {
               long selected = index;
               state.beginColor(name, rgb, value -> list.color(selected, value), now);
               result = true;
            }
         }

         RenderMath.Rect remove = new RenderMath.Rect(row.width - row.height + row.x, row.y, row.height, row.height);
         close(plan, remove, state.hover(remove), shade, 1314272, 1314276, 1314280);
         if (state.click(remove)) {
            removed = index;
         }

         plan.color(shade, shade, shade, c(1314248)).line(row.x, row.y + row.height, row.x + row.width, row.y + row.height);
      }

      if (removed != -1L) {
         list.remove(removed);
         result = true;
      }

      RenderMath.Rect add = new RenderMath.Rect(selection.x, GuiPresentation.unsignedFloat(count) * rowHeight + selection.y, selection.width, rowHeight);
      this.view.text(plan, RenderConstants.text(1314374), add, 1, 0, c(1314236), gray(c(state.hover(add) ? 1314292 : 1314288)));
      if (state.click(add)) {
         if (suggestions != null) {
            state.beginOptions(title, suggestions, index -> {
               if (index != null) {
                  list.addOption(index, suggestions.get(index), new float[]{c(1314296), 0.0F, 0.0F});
               }
            }, now);
         } else {
            state.beginText(title, list, filter, value -> {
               if (value != null) {
                  list.add(value, new float[]{c(1314300), 0.0F, 0.0F});
               }
            }, now);
         }

         result = true;
      }

      return result;
   }

   public static final class Choice {
      public boolean open;
      public float height;
      public double stamp;
   }

   public interface Formatter {
      String format(String var1, double... var2);
   }

   public interface ListData {
      long size();

      String name(long var1);

      float[] color(long var1);

      void color(long var1, float[] var3);

      void add(String var1, float[] var2);

      default void addOption(int index, String name, float[] color) {
         add(name, color);
      }

      void remove(long var1);
   }

   public static final class Range {
      public final PanelGuiState.Slider lower = new PanelGuiState.Slider();
      public final PanelGuiState.Slider upper = new PanelGuiState.Slider();
      public double stamp;
   }
}
