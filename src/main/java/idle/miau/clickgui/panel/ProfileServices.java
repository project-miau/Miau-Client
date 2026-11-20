package idle.miau.clickgui.panel;

import java.io.IOException;
import java.util.List;

public final class ProfileServices {
   private ProfileServices() {
   }

   public static final class Failure extends IOException {
      public final int code;

      public Failure(int code, String message) {
         super(message);
         this.code = code;
      }
   }

   public interface Host {
      List<ProfileServices.Record> records() throws IOException;

      default List<ProfileServices.Record> snapshot() {
         try {
            return records();
         } catch (IOException e) {
            return java.util.Collections.emptyList();
         }
      }

      void load(String name) throws IOException;

      void save(String name, boolean overwrite) throws IOException;

      void delete(String name) throws IOException;

      void rename(String name, String newName) throws IOException;

      void explore() throws IOException;

      void saveConfiguration() throws IOException;

      String current();

      boolean pending();
   }

   public static final class Record {
      public final String name;
      public final String date;
      public final long ageSeconds;
      public Record(String name, long ageSeconds) {
         this.name = name;
         this.ageSeconds = ageSeconds;
         this.date = ProfileMath.date(ageSeconds);
      }
   }
}
