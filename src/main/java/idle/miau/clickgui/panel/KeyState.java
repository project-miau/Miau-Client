package idle.miau.clickgui.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

public final class KeyState {
   private static final String[] NAMES = readNames();

   private KeyState() {
   }

   private static String[] readNames() {
      InputStream stream = Platform.resource("/assets/idle.miau/panel/KeyNames.json");
      if (stream == null) {
         return new String[]{"NONE"};
      }
      try (Reader reader = new InputStreamReader(stream, "UTF-8")) {
         JsonArray rows = new JsonParser().parse(reader).getAsJsonArray();
         String[] result = new String[rows.size()];
         for (int i = 0; i < result.length; i++) {
            result[i] = rows.get(i).getAsString();
         }
         return result;
      } catch (IOException ex) {
         return new String[]{"NONE"};
      }
   }

   public static String displayName(int binding) {
      if (binding <= 0 || binding >= NAMES.length) {
         return NAMES[0];
      }
      return NAMES[binding];
   }
}
