package idle.miau.utility.changelog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ClientChangelogFormatter {
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s*(.+)$");
    private static final Pattern STRONG_HEADING = Pattern.compile("^\\*\\*([^*]+)\\*\\*$");
    private static final Pattern BULLET = Pattern.compile("^(?:[-*+]|\\d+[.)])\\s*");

    private ClientChangelogFormatter() {
    }

    public static List<ClientChangelogLine> parse(String notes) {
        if (notes == null || notes.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<ClientChangelogLine> lines = new ArrayList<ClientChangelogLine>();
        ClientChangelogLine.Type sectionType = ClientChangelogLine.Type.DEFAULT;
        boolean ignoredSection = false;
        for (String rawLine : notes.replace("\r", "").split("\n")) {
            String raw = rawLine.trim();
            if (raw.isEmpty()) {
                continue;
            }

            Matcher heading = HEADING.matcher(raw);
            if (heading.matches()) {
                String title = clean(heading.group(1));
                ignoredSection = isContributorHeading(title);
                if (!ignoredSection && !isGenericHeading(title)) {
                    sectionType = classify(title, ClientChangelogLine.Type.DEFAULT);
                }
                continue;
            }

            Matcher strongHeading = STRONG_HEADING.matcher(raw);
            if (strongHeading.matches()) {
                String title = clean(strongHeading.group(1));
                ClientChangelogLine.Type type = classify(
                        title, ClientChangelogLine.Type.DEFAULT);
                if (isContributorHeading(title)) {
                    ignoredSection = true;
                    continue;
                }
                if (isGenericHeading(title) || type != ClientChangelogLine.Type.DEFAULT) {
                    ignoredSection = false;
                    sectionType = type;
                    continue;
                }
            }

            if (ignoredSection) {
                continue;
            }
            String text = clean(BULLET.matcher(raw).replaceFirst(""));
            if (text.isEmpty() || text.toLowerCase(Locale.ROOT).startsWith("full changelog")) {
                continue;
            }
            lines.add(new ClientChangelogLine(text, classify(text, sectionType)));
        }
        return Collections.unmodifiableList(lines);
    }

    static ClientChangelogLine.Type classify(
            String value, ClientChangelogLine.Type fallback) {
        String text = value.toLowerCase(Locale.ROOT);
        if (containsAny(text,
                "remove", "deleted", "delete ", "xóa", "xoá", "loại bỏ")) {
            return ClientChangelogLine.Type.REMOVED;
        }
        if (containsAny(text,
                "fix", "bug", "crash", "resolved", "hotfix", "sửa", "lỗi")) {
            return ClientChangelogLine.Type.FIXED;
        }
        if (containsAny(text,
                "refactor", "rewrite", "migrat", "renam", "restructur",
                "viết lại", "tái cấu trúc")) {
            return ClientChangelogLine.Type.REFACTORED;
        }
        if (containsAny(text,
                "add", "new ", "introduc", "support", "thêm", "mới", "hỗ trợ")) {
            return ClientChangelogLine.Type.ADDED;
        }
        if (containsAny(text,
                "improv", "enhanc", "updat", "optimi", "rework", "chang",
                "cải thiện", "nâng cấp", "thay đổi", "tối ưu")) {
            return ClientChangelogLine.Type.IMPROVED;
        }
        return fallback;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGenericHeading(String value) {
        String text = value.toLowerCase(Locale.ROOT).replace("'", "").trim();
        return text.equals("whats changed")
                || text.equals("changes")
                || text.equals("change")
                || text.equals("changelog")
                || text.equals("release notes");
    }

    private static boolean isContributorHeading(String value) {
        String text = value.toLowerCase(Locale.ROOT).trim();
        return text.equals("contributors")
                || text.equals("new contributors")
                || text.equals("contributor")
                || text.equals("new contributor");
    }

    private static String clean(String value) {
        String result = value;
        Matcher link = MARKDOWN_LINK.matcher(result);
        StringBuffer links = new StringBuffer();
        while (link.find()) {
            link.appendReplacement(links, Matcher.quoteReplacement(link.group(1)));
        }
        link.appendTail(links);
        return links.toString()
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
