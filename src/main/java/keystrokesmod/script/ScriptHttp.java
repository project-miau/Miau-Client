package keystrokesmod.script;

import idle.miau.utility.NetworkUtils;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class ScriptHttp {
    private ScriptHttp() {
    }

    static String load(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("id", loadId(url, System.currentTimeMillis(),
                    System.getenv("COMPUTERNAME"), System.getenv("PROCESSOR_IDENTIFIER"),
                    System.getenv("PROCESSOR_LEVEL"), Runtime.getRuntime().availableProcessors()));
            return NetworkUtils.getTextFromConnection(connection, true);
        } catch (Exception exception) {
            idle.miau.utility.Utils.log.warn("Unable to fetch script load directive", exception);
            return "";
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    static String loadId(String url, long time, String computerName, String processorIdentifier,
                         String processorLevel, int processors) throws Exception {
        String input = (time / 20000L + 29062381L) + "J{LlrPhHgj8zy:uB" + computerName
                + processorIdentifier + processorLevel + processors + url;
        return String.format("%032x", new BigInteger(1, MessageDigest.getInstance("MD5")
                .digest(input.getBytes(StandardCharsets.UTF_8))));
    }
}
