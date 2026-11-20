package idle.miau.clickgui.panel;

import idle.miau.Miau;
import idle.miau.utility.config.Config;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MimeowProfileHost implements ProfileServices.Host {
   @Override
   public List<ProfileServices.Record> records() throws IOException {
      if (Miau.configManager == null || Miau.configManager.configs == null) {
         return Collections.emptyList();
      }
      List<ProfileServices.Record> records = new ArrayList<>();
      for (Config config : Miau.configManager.configs) {
         if (config == null) {
            continue;
         }
         String name = config.getName();
         long age = 0L;
         if (Miau.configManager.directory != null) {
            File file = new File(Miau.configManager.directory, name + ".json");
            if (file.exists()) {
               age = Math.max(0L, (System.currentTimeMillis() - file.lastModified()) / 1000L);
            }
         }
         records.add(new ProfileServices.Record(name, age));
      }
      return records;
   }

   @Override
   public void load(String name) throws IOException {
      if (Miau.configManager != null) {
         Miau.configManager.loadConfig(name);
      }
   }

   @Override
   public void save(String name, boolean overwrite) throws IOException {
      if (Miau.configManager != null) {
         Config cfg = Miau.configManager.getConfig(name);
         if (cfg != null) {
            Miau.configManager.saveConfig(cfg);
         } else {
            Miau.configManager.createConfig(name, 0);
         }
      }
   }

   @Override
   public void delete(String name) throws IOException {
      if (Miau.configManager != null) {
         Miau.configManager.deleteConfig(name);
      }
   }

   @Override
   public void explore() throws IOException {
      if (Desktop.isDesktopSupported() && Miau.configManager != null && Miau.configManager.directory != null) {
         Desktop.getDesktop().open(Miau.configManager.directory);
      }
   }

   @Override
   public void saveConfiguration() throws IOException {
      if (Miau.configManager != null && Miau.currentConfig != null) {
         Miau.configManager.saveConfig(Miau.currentConfig);
      }
   }

   @Override
   public void rename(String name, String newName) throws IOException {
      if (Miau.configManager == null) {
         throw new IOException("Configuration manager is unavailable.");
      }
      Config config = Miau.configManager.getConfig(name);
      if (config == null || !Miau.configManager.renameConfig(config, newName)) {
         throw new IOException("Could not rename profile: " + name);
      }
   }

   @Override
   public String current() {
      return Miau.currentConfig != null ? Miau.currentConfig.getName() : "default";
   }

   @Override
   public boolean pending() {
      return false;
   }
}
