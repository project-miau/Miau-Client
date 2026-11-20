package idle.miau.clickgui.panel;

import idle.miau.clickgui.GuiCategories;
import idle.miau.module.Module;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public final class GuiAppearance {
   public static final int PROFILE_PAGE = Module.category.configs.ordinal();
   private static final List<Module.category> CATEGORIES = Stream.concat(
         GuiCategories.modules().stream(), Stream.of(Module.category.configs)).toList();
   public static final String[] TABS = Stream.concat(
         CATEGORIES.stream().map(GuiCategories::title), Stream.of("Unload")).toArray(String[]::new);

   public static int category(int tab) {
      return CATEGORIES.get(tab).ordinal();
   }

   public static int tab(int category) {
      Module.category[] values = Module.category.values();
      int index = category >= 0 && category < values.length ? CATEGORIES.indexOf(values[category]) : -1;
      return index < 0 ? CATEGORIES.indexOf(Module.category.other) : index;
   }

   public static int tabColumns(float width) {
      return Math.max(1, Math.min(6, (int) (width / 120.0F)));
   }

   public static int tabRows(float width) {
      return (TABS.length + tabColumns(width) - 1) / tabColumns(width);
   }

   public static RenderMath.Rect tabBounds(RenderMath.Rect area, int index) {
      int columns = tabColumns(area.width);
      float width = area.width / columns;
      float height = area.height / tabRows(area.width);
      return new RenderMath.Rect(area.x + index % columns * width,
            area.y + index / columns * height, width, height);
   }

   public static List<Module> entries(int page) {
      Module.category[] values = Module.category.values();
      return page >= 0 && page < values.length && CATEGORIES.contains(values[page])
            ? GuiCategories.moduleEntries(values[page]) : Collections.emptyList();
   }

   public static String description(Module module) {
      return module == null ? "" : module.getDescription();
   }

   private GuiAppearance() {}
}
