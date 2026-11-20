package idle.miau.utility.irc;

final class IrcMessageFormatter {
    private IrcMessageFormatter() {
    }

    static String format(IrcMessage message) {
        String source = message.getSource();
        String role = roleTag(message.getRole());
        StringBuilder prefix = new StringBuilder();
        if ("discord".equals(source)) {
            prefix.append("\u00a77[\u00a7bDiscord\u00a77] ");
            if (!role.isEmpty()) {
                prefix.append(role).append(' ');
            }
        }
        else if ("dashboard".equals(source)) {
            prefix.append(role.isEmpty() ? "\u00a77[\u00a7eDashboard\u00a77]" : role)
                    .append(' ');
        }
        else {
            prefix.append("\u00a77[\u00a7dIRC\u00a77] ");
        }
        return prefix.append("\u00a7f")
                .append(message.getUsername())
                .append("\u00a77: \u00a7f")
                .append(message.getContent())
                .toString();
    }

    private static String roleTag(String role) {
        if ("owner".equals(role)) return "\u00a77[\u00a74Owner\u00a77]";
        if ("dev".equals(role)) return "\u00a77[\u00a7cDev\u00a77]";
        if ("manager".equals(role)) return "\u00a77[\u00a76Manager\u00a77]";
        if ("helper".equals(role)) return "\u00a77[\u00a7aHelper\u00a77]";
        if ("media".equals(role)) return "\u00a77[\u00a7bMedia\u00a77]";
        if ("friends".equals(role)) return "\u00a77[\u00a7dFriends\u00a77]";
        return "";
    }
}
