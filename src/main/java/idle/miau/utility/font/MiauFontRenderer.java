package idle.miau.utility.font;

public interface MiauFontRenderer {
    @FunctionalInterface
    interface GlyphColorProvider {
        int colorForGlyph(char character, float xOffset, float width, Integer formattingColor);
    }

    int drawString(String text, float x, float y, int color, boolean shadow);

    int drawGlyphString(String text, float x, float y, GlyphColorProvider colorProvider, boolean shadow);

    default int drawString(String text, float x, float y, int color) {
        return drawString(text, x, y, color, false);
    }

    default int drawGlyphString(String text, float x, float y, GlyphColorProvider colorProvider) {
        return drawGlyphString(text, x, y, colorProvider, false);
    }

    default int drawStringWithShadow(String text, float x, float y, int color) {
        return drawString(text, x, y, color, true);
    }

    int getStringWidth(String text);

    default String trimStringToWidth(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) return "";
        int end = 0;
        while (end < text.length()) {
            int next = end + Character.charCount(text.codePointAt(end));
            if (text.charAt(end) == '\u00A7' && next < text.length()) next++;
            if (getStringWidth(text.substring(0, next)) > width) break;
            end = next;
        }
        return text.substring(0, end);
    }

    default String trimStringToWidth(String text, int width, boolean reverse) {
        if (!reverse) return trimStringToWidth(text, width);
        if (text == null || width <= 0) return "";
        int start = text.length();
        while (start > 0) {
            int previous = text.offsetByCodePoints(start, -1);
            if (getStringWidth(text.substring(previous)) > width) break;
            start = previous;
        }
        return text.substring(start);
    }

    int getFontHeight();

    default int getLineHeight() {
        return getFontHeight();
    }

    default float getLineHeightExact() {
        return getLineHeight();
    }

    default int getTextTopOffset() {
        return 0;
    }

    default int getTextBottomOffset() {
        return getFontHeight();
    }

    default void destroy() {
    }
}
