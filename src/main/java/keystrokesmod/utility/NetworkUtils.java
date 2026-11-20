package keystrokesmod.utility;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

public class NetworkUtils {
    public static final String CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern OGP_IMAGE_REGEX = Pattern.compile("<meta property=\"(?:og:image|twitter:image)\" content=\"(?<url>.+?)\"[^>]*?>");
    private static final Pattern IMG_TAG_REGEX = Pattern.compile("<img[^>]*?src=\"(?<url>.+?)\"[^>]*?>");

    public static String getTextFromURL(String _url, boolean appendNewline, boolean sendHardwareId) {
        String contents = "";
        HttpURLConnection con = null;

        try {
            URL url = new URL(_url);
            con = (HttpURLConnection) url.openConnection();
            con.setRequestProperty("User-Agent", CHROME_USER_AGENT);
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
                StringBuilder stringBuilder = new StringBuilder();
                String input;

                while ((input = bufferedReader.readLine()) != null) {
                    stringBuilder.append(input);
                    if (appendIndent) {
                        stringBuilder.append("\n");
                    }
                }
                return stringBuilder.toString();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    public static BufferedImage getImageFromURL(String urlString) {
        try {
            URL url = new URL(urlString);
            while (true) {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(true);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", CHROME_USER_AGENT);
                connection.setRequestProperty("Accept", "text/html, image/*");
                if (url.getHost() != null && url.getHost().contains("imgur")) {
                    connection.setRequestProperty("Referer", "https://imgur.com/");
                }
                int responseCode = connection.getResponseCode();
                if (responseCode >= 300 && responseCode < 400) {
                    String newLocation = connection.getHeaderField("Location");
                    if (newLocation == null || newLocation.isEmpty()) {
                        return null;
                    }
                    url = new URL(url, newLocation);
                    connection.disconnect();
                    continue;
                }
                String contentType = connection.getContentType();
                if (contentType != null && contentType.startsWith("image")) {
                    try (InputStream inputStream = connection.getInputStream()) {
                        BufferedImage image = ImageIO.read(inputStream);
                        connection.disconnect();
                        return image;
                    }
                }
                try (InputStream inputStream = connection.getInputStream()) {
                    String body = new String(inputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    String imageURL = "";
                    java.util.regex.Matcher matcher = OGP_IMAGE_REGEX.matcher(body);
                    if (matcher.find()) {
                        imageURL = matcher.group("url");
                    } else {
                        matcher = IMG_TAG_REGEX.matcher(body);
                        if (matcher.find()) {
                            imageURL = matcher.group("url");
                        }
                    }
                    if (imageURL.isEmpty()) {
                        return null;
                    }
                    url = new URL(url, imageURL);
                    connection.disconnect();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
