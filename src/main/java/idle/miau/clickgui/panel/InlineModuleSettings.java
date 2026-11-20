package idle.miau.clickgui.panel;

import idle.miau.Miau;
import idle.miau.module.Module;
import idle.miau.module.setting.Setting;
import idle.miau.module.setting.SettingGroups;
import idle.miau.module.setting.impl.BlockListSetting;
import idle.miau.module.setting.impl.ButtonSetting;
import idle.miau.module.setting.impl.ColorSetting;
import idle.miau.module.setting.impl.DescriptionSetting;
import idle.miau.module.setting.impl.GroupSetting;
import idle.miau.module.setting.impl.ItemChoiceSetting;
import idle.miau.module.setting.impl.ItemListSetting;
import idle.miau.module.setting.impl.MultiSelectSetting;
import idle.miau.module.setting.impl.PotionListSetting;
import idle.miau.module.setting.impl.RangeSetting;
import idle.miau.module.setting.impl.SliderSetting;
import idle.miau.module.setting.impl.StringListSetting;
import idle.miau.module.setting.impl.TextSetting;
import idle.miau.utility.BlockSearchIndex;
import idle.miau.utility.ItemSearchIndex;
import idle.miau.utility.PotionSearchIndex;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class InlineModuleSettings {
   private final GuiWidgets widgets;
   private final Map<Setting, InlineModuleSettings.Record> records = new IdentityHashMap<>();

   public InlineModuleSettings(GuiPresentation presentation, GuiPresentation.Tooltip tooltip) {
      this.widgets = new GuiWidgets(presentation, tooltip, (format, values) -> {
         Object[] args = new Object[values.length];
         for (int i = 0; i < values.length; i++) {
            args[i] = values[i];
         }
         try {
            return String.format(Locale.ROOT, format.replace("%0.", "%."), args);
         } catch (Exception e) {
            if (values.length == 1) {
               return String.format(Locale.ROOT, "%.2f", values[0]);
            } else if (values.length >= 2) {
               return String.format(Locale.ROOT, "%.2f - %.2f", values[0], values[1]);
            }
            return format;
         }
      });
   }

   private static String safeSuffix(String suffix) {
      if (suffix == null || suffix.trim().isEmpty()) {
         return "";
      }
      return " " + suffix.trim().replace("%", "%%");
   }

   public void render(Module module, PanelGuiState.Runtime state, DrawPlan plan, PanelGuiState.Layout layout, float[] accent, double now) {
      if (module == null || module.getSettings() == null) {
         return;
      }
      this.renderSettings(module, null, state, plan, layout, accent, now,
         Collections.newSetFromMap(new IdentityHashMap<>()));
   }

   private void renderSettings(Module module, GroupSetting parent, PanelGuiState.Runtime state, DrawPlan plan,
                               PanelGuiState.Layout layout, float[] accent, double now, Set<Setting> rendered) {
      for (Setting setting : module.getSettings()) {
         if (setting == null || !setting.visible || SettingGroups.parentOf(setting, module.getSettings()) != parent
             || !rendered.add(setting)) {
            continue;
         }
         this.renderSetting(module, setting, state, plan, layout, accent, now, rendered);
      }
   }

   private Record record(Setting setting) {
      Record r = this.records.get(setting);
      if (r == null) {
         r = new Record();
         this.records.put(setting, r);
      }
      return r;
   }

   private void renderSetting(Module module, Setting setting, PanelGuiState.Runtime state, DrawPlan plan,
                              PanelGuiState.Layout layout, float[] accent, double now, Set<Setting> rendered) {
      Record r = this.record(setting);
      if (setting instanceof ButtonSetting) {
         ButtonSetting button = (ButtonSetting)setting;
         if (button.isMethodButton) {
            boolean clicked = this.widgets.checkbox(state, plan, layout, button.getName(), r.toggle, false, accent, now);
            if (clicked) {
               button.runMethod();
            }
         } else {
            boolean current = button.isToggled();
            boolean next = this.widgets.checkbox(state, plan, layout, button.getName(), r.toggle, current, accent, now);
            if (next != current) {
               button.setEnabled(next);
               module.guiButtonToggled(button);
               module.guiUpdate();
               Miau.markCurrentConfigUnsaved();
            }
         }
      } else if (setting instanceof SliderSetting) {
         SliderSetting slider = (SliderSetting)setting;
         if (slider instanceof ItemChoiceSetting items && items.getChoices().size() >= 6) {
            List<String> choices = items.getChoices();
            int current = Math.max(0, Math.min(choices.size() - 1, (int) slider.getInput()));
            this.widgets.itemChoice(state, plan, layout, slider.getName(), choices.get(current), choices, selected -> {
               if (selected == null) return;
               slider.setValue(selected);
               module.onSlide(slider);
               module.guiUpdate();
               Miau.markCurrentConfigUnsaved();
            }, now);
         } else if (slider.isString && slider.getOptions() != null && slider.getOptions().length > 0) {
            String[] options = slider.getOptions();
            int current = Math.max(0, Math.min(options.length - 1, (int)Math.round(slider.getInput())));
            r.selected[0] = current;
            this.widgets.choice(state, plan, layout, slider.getName(), r.choice,
               Arrays.asList(options), r.selected, 0, accent, now);
            if (r.selected[0] != current) {
               slider.setValue(r.selected[0]);
               module.onSlide(slider);
               module.guiUpdate();
               Miau.markCurrentConfigUnsaved();
            }
         } else {
            String suffix = slider.getSuffix();
            String format;
            if (slider.getIntervals() >= 1.0 && slider.getIntervals() == Math.floor(slider.getIntervals())) {
               format = "%.0f" + safeSuffix(suffix);
            } else {
               format = "%.2f" + safeSuffix(suffix);
            }
            float current = (float)slider.getInput();
            float next = this.widgets.number(
               state,
               plan,
               layout,
               slider.getName(),
               format,
               r.lower,
               r.number,
               (float)slider.getMin(),
               (float)slider.getMax(),
               (float)slider.getIntervals(),
               0.0F,
               current,
               accent,
               now
            );
            if (Float.floatToRawIntBits(next) != Float.floatToRawIntBits(current)) {
               slider.setValue(next);
               module.onSlide(slider);
               module.guiUpdate();
               Miau.markCurrentConfigUnsaved();
            }
         }
      } else if (setting instanceof RangeSetting) {
         RangeSetting range = (RangeSetting)setting;
         String suffix = range.getSuffix();
         String format = "%.2f" + safeSuffix(suffix);
         String pairFormat = "%.2f - %.2f" + safeSuffix(suffix);
         float min = (float)range.getMinimum();
         float max = (float)range.getMaximum();
         float[] next = this.widgets.range(
            state,
            plan,
            layout,
            range.getName(),
            format,
            pairFormat,
            r.lower,
            r.upper,
            r.range,
            (float)range.getMinBound(),
            (float)range.getMaxBound(),
            (float)range.getInterval(),
            0.0F,
            min,
            max,
            accent,
            now
         );
         if (next[0] != min || next[1] != max) {
            range.setRange(next[0], next[1]);
            Miau.markCurrentConfigUnsaved();
         }
      } else if (setting instanceof ColorSetting) {
         ColorSetting color = (ColorSetting)setting;
         float[] current = new float[]{
            color.getRed() / 255.0F,
            color.getGreen() / 255.0F,
            color.getBlue() / 255.0F
         };
         this.widgets.color(state, plan, layout, color.getName(), current, rgb -> {
            color.setColor((int)(rgb[0] * 255.0F), (int)(rgb[1] * 255.0F), (int)(rgb[2] * 255.0F));
            Miau.markCurrentConfigUnsaved();
         }, now);
      } else if (setting instanceof MultiSelectSetting) {
         MultiSelectSetting multi = (MultiSelectSetting)setting;
         if (this.widgets.multiChoice(state, plan, layout, multi.getName(), r.choice,
            Arrays.asList(multi.getOptions()), multi::isSelected, multi::toggle, accent, now)) {
            module.onCommandSettingChanged(multi);
            module.guiUpdate();
            Miau.markCurrentConfigUnsaved();
         }
      } else if (setting instanceof GroupSetting) {
         GroupSetting group = (GroupSetting)setting;
         boolean opened = this.widgets.group(state, plan, layout, group.getName(), group.isOpened());
         if (opened != group.isOpened()) {
            group.setOpened(opened);
            module.guiUpdate();
         }
         if (opened) {
            this.renderSettings(module, group, state, plan, layout, accent, now, rendered);
         }
      } else if (setting instanceof DescriptionSetting) {
         DescriptionSetting desc = (DescriptionSetting)setting;
         this.widgets.section(plan, layout, desc.getDesc());
      } else if (setting instanceof StringListSetting) {
         StringListSetting list = (StringListSetting)setting;
         this.widgets.list(
            state,
            plan,
            layout,
            list.getName(),
            list.getName(),
            PanelGuiState::catalogFilter,
            0,
            new GuiWidgets.ListData() {
               @Override
               public long size() {
                  return list.getEntries().size();
               }

               @Override
               public String name(long index) {
                  return list.getEntries().get((int)index);
               }

               @Override
               public float[] color(long index) {
                  return new float[]{1.0F, 1.0F, 1.0F};
               }

               @Override
               public void color(long index, float[] rgb) {
               }

               @Override
               public void add(String name, float[] rgb) {
                  list.addEntry(name);
                  Miau.markCurrentConfigUnsaved();
               }

               @Override
               public void remove(long index) {
                  List<String> entries = list.getEntries();
                  if (index >= 0 && index < entries.size()) {
                     list.removeEntry(entries.get((int)index));
                     Miau.markCurrentConfigUnsaved();
                  }
               }
            },
            null,
            now
         );
      } else if (setting instanceof BlockListSetting) {
         BlockListSetting list = (BlockListSetting)setting;
         if (r.catalog == null) {
            r.catalog = PanelItemCatalog.entries(list);
            r.catalogChoices = r.catalog.stream().map(PanelItemCatalog.Entry::label).toList();
         }
         List<PanelItemCatalog.Entry> catalog = r.catalog;
         this.widgets.list(
            state,
            plan,
            layout,
            list.getName(),
            list.getName(),
            PanelGuiState::catalogFilter,
            0,
            new GuiWidgets.ListData() {
               @Override
               public long size() {
                  return list.getBlocks().size();
               }

               @Override
               public String name(long index) {
                  String value = list.getBlocks().get((int) index);
                  return list instanceof PotionListSetting ? PotionSearchIndex.getDisplayName(value)
                     : list instanceof ItemListSetting ? ItemSearchIndex.getDisplayName(value)
                     : BlockSearchIndex.getDisplayName(value);
               }

               @Override
               public float[] color(long index) {
                  return new float[]{1.0F, 1.0F, 1.0F};
               }

               @Override
               public void color(long index, float[] rgb) {
               }

               @Override
               public void add(String name, float[] rgb) {
                  if (list instanceof ItemListSetting items) items.addItem(name);
                  else list.addBlock(name);
                  Miau.markCurrentConfigUnsaved();
               }

               @Override
               public void addOption(int index, String name, float[] rgb) {
                  add(catalog.get(index).value(), rgb);
               }

               @Override
               public void remove(long index) {
                  List<String> blocks = list.getBlocks();
                  if (index >= 0 && index < blocks.size()) {
                     if (list instanceof ItemListSetting items) items.removeItem(blocks.get((int) index));
                     else list.removeBlock(blocks.get((int) index));
                     Miau.markCurrentConfigUnsaved();
                  }
               }
            },
            r.catalogChoices,
            now
         );
      } else if (setting instanceof TextSetting) {
         TextSetting text = (TextSetting)setting;
         String value = text.getText().isEmpty() ? text.getPlaceholder() : text.getText();
         if (text.isSensitive() && text.isMasked()) {
            value = "*".repeat(text.getText().length());
         }
         this.widgets.text(state, plan, layout, text.getName(), value, text.getText(), r.lower,
            text.getMaxLength(), next -> {
               if (next != null) {
                  text.setText(next);
                  text.submit();
                  module.onCommandSettingChanged(text);
                  module.guiUpdate();
                  Miau.markCurrentConfigUnsaved();
               }
            }, now);
      }
   }

   private static final class Record {
      final Object lower = new Object();
      final Object upper = new Object();
      final PanelGuiState.Toggle toggle = new PanelGuiState.Toggle();
      final PanelGuiState.Slider number = new PanelGuiState.Slider();
      final GuiWidgets.Range range = new GuiWidgets.Range();
      final GuiWidgets.Choice choice = new GuiWidgets.Choice();
      final int[] selected = new int[]{0};
      List<PanelItemCatalog.Entry> catalog;
      List<String> catalogChoices;
   }
}
