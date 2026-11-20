package idle.miau.utility;

import net.minecraft.client.renderer.texture.TextureUtil;
import org.apache.commons.io.IOUtils;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkUtils {
    public static String API_KEY = "";

    private static final Pattern OGP_IMAGE_REGEX = Pattern.compile("<meta property=\"(?:og:image|twitter:image)\" content=\"(?<url>.+?)\".*?/?>");
    private static final Pattern IMG_TAG_REGEX = Pattern.compile("<img.*?src=\"(?<url>.+?)\".*?>");

    public static final String CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static boolean isHypixelKeyValid(String ak) {
        String contents = getTextFromURL("https://api.hypixel.net/key?key=" + ak, false);
        return !contents.isEmpty() && !contents.contains("Invalid");
    }

    public static String getTextFromURL(String _url, boolean appendNewline) {
        String contents = "";
        HttpURLConnection con = null;

        try {
            URL url = new URL(_url);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(10000);
            contents = getTextFromConnection(con, appendNewline);
        } catch (IOException ignored) {
        } finally {
            if (con != null) {
                con.disconnect();
            }

        }
        return contents;
    }

    public static String getTextFromConnection(HttpURLConnection connection, boolean appendIndent) {
        if (connection != null) {
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                String result;
                try {
                    StringBuilder stringBuilder = new StringBuilder();

                    String input;
                    while ((input = bufferedReader.readLine()) != null) {
                        stringBuilder.append(input + (appendIndent ? "\n" : ""));
                    }

                    String res = stringBuilder.toString();
                    connection.disconnect();

                    result = res;
                } finally {
                    bufferedReader.close();
                }

                return result;
            } catch (Exception ignored) {
            }
        }

        return "";
    }

    public static BufferedImage getImageFromURL(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection;

            for (int redirects = 0; redirects < 8; redirects++) {
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(true);
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", CHROME_USER_AGENT);
                connection.setRequestProperty("Accept", "text/html, image/*");

                if (url.getHost().contains("imgur")) {
                    connection.setRequestProperty("Referer", "https://imgur.com/");
                }

                int responseCode = connection.getResponseCode();

                if (responseCode >= 300 && responseCode < 400) {
                    String newLocation = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (newLocation == null || newLocation.isEmpty()) {
                        return null;
                    }
                    url = new URL(url, newLocation);
                    continue;
                }

                String contentType = connection.getContentType();
                if (contentType != null && contentType.startsWith("image")) {
                    try (InputStream inputStream = connection.getInputStream()) {
                        return TextureUtil.readBufferedImage(inputStream);
                    }
                    finally {
                        connection.disconnect();
                    }
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    String body = IOUtils.toString(inputStream, "UTF-8");
                    String imageURL = "";

                    Matcher matcher = OGP_IMAGE_REGEX.matcher(body);
                    if (matcher.find()) {
                        imageURL = matcher.group("url");
                    }
                    else {
                        matcher = IMG_TAG_REGEX.matcher(body);
                        if (matcher.find()) {
                            imageURL = matcher.group("url");
                        }
                    }

                    if (imageURL.isEmpty()) {
                        return null;
                    }

                    url = new URL(url, imageURL);
                }
                finally {
                    connection.disconnect();
                }
            }
            return null;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
