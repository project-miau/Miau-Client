package dev.idle.igam.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class CrackedAccountGenerator {
  private static final String RANDOM_API_ENDPOINT = "https://randomapi.dev/api/names?count=1";
  private static final String RANDOM_USER_ENDPOINT = "https://randomuser.me/api/?inc=name";
  private static final int CONNECT_TIMEOUT = 4000;
  private static final int READ_TIMEOUT = 5000;
  private static final int MAX_USERNAME_LENGTH = 16;
  private static final int MIN_USERNAME_LENGTH = 3;
  private static final Random RANDOM = new Random();

  private CrackedAccountGenerator() {
  }

  public static String nextUsername(Collection<Account> accounts) throws IOException {
    List<NameSource> sources = new ArrayList<>(Arrays.asList(NameSource.values()));
    Collections.shuffle(sources, RANDOM);
    IOException lastError = null;
    for (NameSource source : sources) {
      try {
        return source.fetchUsername(accounts);
      } catch (IOException exception) {
        lastError = exception;
      }
    }
    throw lastError != null
        ? lastError
        : new IOException("No username source is available");
  }

  private enum NameSource {
    RANDOM_API(RANDOM_API_ENDPOINT) {
      @Override
      String extractUsername(JsonElement response) throws IOException {
        if (!response.isJsonObject()) {
          throw new IOException("Random API returned an invalid response");
        }
        JsonArray entries = response.getAsJsonObject().getAsJsonArray("data");
        if (entries == null || entries.size() == 0 || !entries.get(0).isJsonObject()) {
          throw new IOException("Random API returned no names");
        }
        JsonObject entry = entries.get(0).getAsJsonObject();
        String username = value(entry, "firstName") + value(entry, "lastName");
        if (username.isEmpty()) {
          username = value(entry, "fullName");
        }
        if (username.isEmpty()) {
          throw new IOException("Random API returned an empty name");
        }
        return username;
      }
    },
    RANDOM_USER(RANDOM_USER_ENDPOINT) {
      @Override
      String extractUsername(JsonElement response) throws IOException {
        if (!response.isJsonObject()) {
          throw new IOException("Random User API returned an invalid response");
        }
        JsonArray results = response.getAsJsonObject().getAsJsonArray("results");
        if (results == null || results.size() == 0 || !results.get(0).isJsonObject()) {
          throw new IOException("Random User API returned no users");
        }
        JsonObject name = results.get(0).getAsJsonObject().getAsJsonObject("name");
        String username = value(name, "first") + value(name, "last");
        if (username.isEmpty()) {
          throw new IOException("Random User API returned an empty name");
        }
        return username;
      }
    };

    private final String endpoint;

    NameSource(String endpoint) {
      this.endpoint = endpoint;
    }

    String fetchUsername(Collection<Account> accounts) throws IOException {
      String username = normalize(extractUsername(fetch()));
      if (username.length() < MIN_USERNAME_LENGTH) {
        throw new IOException(this.endpoint + " returned an unusable username");
      }
      return makeUnique(username, accounts);
    }

    abstract String extractUsername(JsonElement response) throws IOException;

    private JsonElement fetch() throws IOException {
      HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(CONNECT_TIMEOUT);
      connection.setReadTimeout(READ_TIMEOUT);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty("User-Agent", "Miau-Client");
      connection.setUseCaches(false);

      try {
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
          throw new IOException(endpoint + " returned HTTP " + status);
        }
        try (InputStream input = connection.getInputStream();
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
          JsonElement parsed;
          try {
            parsed = new JsonParser().parse(reader);
          } catch (RuntimeException exception) {
            throw new IOException(endpoint + " returned invalid JSON", exception);
          }
          return parsed;
        }
      } finally {
        connection.disconnect();
      }
    }
  }

  private static String value(JsonObject object, String name) {
    if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
      return "";
    }
    return object.get(name).getAsString();
  }

  private static String normalize(String username) {
    StringBuilder normalized = new StringBuilder();
    for (int index = 0; index < username.length(); index++) {
      char character = username.charAt(index);
      if ((character >= 'a' && character <= 'z')
          || (character >= 'A' && character <= 'Z')
          || (character >= '0' && character <= '9')
          || character == '_') {
        normalized.append(character);
      }
      if (normalized.length() == MAX_USERNAME_LENGTH) {
        break;
      }
    }
    return normalized.toString();
  }

  private static String makeUnique(String username, Collection<Account> accounts) throws IOException {
    Set<String> existingNames = new HashSet<>();
    if (accounts != null) {
      for (Account account : accounts) {
        if (account != null && account.getUsername() != null) {
          existingNames.add(account.getUsername().toLowerCase(Locale.ROOT));
        }
      }
    }

    if (!existingNames.contains(username.toLowerCase(Locale.ROOT))) {
      return username;
    }

    for (int suffix = 2; suffix < 1000; suffix++) {
      String suffixText = Integer.toString(suffix);
      int baseLength = Math.min(username.length(), MAX_USERNAME_LENGTH - suffixText.length());
      String candidate = username.substring(0, baseLength) + suffixText;
      if (!existingNames.contains(candidate.toLowerCase(Locale.ROOT))) {
        return candidate;
      }
    }
    throw new IOException("Unable to create a unique cracked username");
  }
}
