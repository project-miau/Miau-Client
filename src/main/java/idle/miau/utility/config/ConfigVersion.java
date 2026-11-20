package idle.miau.utility.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import idle.miau.BuildVersion;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConfigVersion {
    static final String KEY = "version";

    private static final Pattern VERSION = Pattern.compile("^v?(\\d+(?:\\.\\d+)*)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUALIFIER_PART = Pattern.compile("[a-z]+|\\d+", Pattern.CASE_INSENSITIVE);

    private ConfigVersion() {
    }

    static void write(JsonObject object) {
        object.addProperty(KEY, BuildVersion.VERSION);
    }

    static String read(JsonObject object) {
        if (object == null) {
            return "";
        }
        String version = readString(object.get(KEY));
        return version.isEmpty() ? readString(object.get("clientVersion")) : version;
    }

    static boolean isOlderThanClient(JsonObject object) {
        return isOlder(read(object), BuildVersion.VERSION);
    }

    static boolean isOlder(String savedVersion, String clientVersion) {
        ParsedVersion saved = ParsedVersion.parse(savedVersion);
        ParsedVersion client = ParsedVersion.parse(clientVersion);
        return saved != null && client != null && saved.compareTo(client) < 0;
    }

    private static String readString(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            return "";
        }
        try {
            String text = value.getAsString();
            return text == null ? "" : text.trim();
        }
        catch (Exception ignored) {
            return "";
        }
    }

    private static final class ParsedVersion implements Comparable<ParsedVersion> {
        private final List<BigInteger> numbers;
        private final String qualifier;

        private ParsedVersion(List<BigInteger> numbers, String qualifier) {
            this.numbers = numbers;
            this.qualifier = qualifier;
        }

        static ParsedVersion parse(String value) {
            if (value == null) {
                return null;
            }
            Matcher matcher = VERSION.matcher(value.trim());
            if (!matcher.matches()) {
                return null;
            }
            List<BigInteger> numbers = new ArrayList<BigInteger>();
            for (String number : matcher.group(1).split("\\.")) {
                numbers.add(new BigInteger(number));
            }
            String remainder = matcher.group(2).trim();
            int buildMetadata = remainder.indexOf('+');
            if (buildMetadata >= 0) {
                remainder = remainder.substring(0, buildMetadata);
            }
            String qualifier = remainder.replaceFirst("^[._-]+", "").toLowerCase(Locale.ROOT);
            return new ParsedVersion(numbers, qualifier);
        }

        @Override
        public int compareTo(ParsedVersion other) {
            int length = Math.max(numbers.size(), other.numbers.size());
            for (int index = 0; index < length; index++) {
                BigInteger left = index < numbers.size() ? numbers.get(index) : BigInteger.ZERO;
                BigInteger right = index < other.numbers.size() ? other.numbers.get(index) : BigInteger.ZERO;
                int compared = left.compareTo(right);
                if (compared != 0) {
                    return compared;
                }
            }
            return compareQualifiers(qualifier, other.qualifier);
        }

        private static int compareQualifiers(String left, String right) {
            if (left.equals(right)) {
                return 0;
            }
            if (left.isEmpty()) {
                return 1;
            }
            if (right.isEmpty()) {
                return -1;
            }

            List<String> leftParts = qualifierParts(left);
            List<String> rightParts = qualifierParts(right);
            int length = Math.max(leftParts.size(), rightParts.size());
            for (int index = 0; index < length; index++) {
                if (index >= leftParts.size()) {
                    return -1;
                }
                if (index >= rightParts.size()) {
                    return 1;
                }
                int compared = compareQualifierPart(leftParts.get(index), rightParts.get(index));
                if (compared != 0) {
                    return compared;
                }
            }
            return 0;
        }

        private static List<String> qualifierParts(String qualifier) {
            List<String> parts = new ArrayList<String>();
            Matcher matcher = QUALIFIER_PART.matcher(qualifier);
            while (matcher.find()) {
                parts.add(matcher.group().toLowerCase(Locale.ROOT));
            }
            if (parts.isEmpty()) {
                parts.add(qualifier);
            }
            return parts;
        }

        private static int compareQualifierPart(String left, String right) {
            boolean leftNumber = Character.isDigit(left.charAt(0));
            boolean rightNumber = Character.isDigit(right.charAt(0));
            if (leftNumber && rightNumber) {
                return new BigInteger(left).compareTo(new BigInteger(right));
            }
            if (leftNumber != rightNumber) {
                return leftNumber ? 1 : -1;
            }
            int leftRank = qualifierRank(left);
            int rightRank = qualifierRank(right);
            return leftRank == rightRank ? left.compareTo(right) : Integer.compare(leftRank, rightRank);
        }

        private static int qualifierRank(String value) {
            if ("snapshot".equals(value) || "dev".equals(value)) {
                return 0;
            }
            if ("alpha".equals(value) || "a".equals(value)) {
                return 1;
            }
            if ("beta".equals(value) || "b".equals(value)) {
                return 2;
            }
            if ("milestone".equals(value) || "m".equals(value)) {
                return 3;
            }
            if ("rc".equals(value) || "cr".equals(value)) {
                return 4;
            }
            if ("final".equals(value) || "ga".equals(value) || "release".equals(value)) {
                return 6;
            }
            return 5;
        }
    }
}
