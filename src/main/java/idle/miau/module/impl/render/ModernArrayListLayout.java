package idle.miau.module.impl.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

final class ModernArrayListLayout {
    final List<Row> rows = new ArrayList<>();
    final List<Border> borders = new ArrayList<>();
    final float[] progress;
    float left;
    float top;
    float right;
    float bottom;

    ModernArrayListLayout(float[] widths, float[] progress, float anchorX, float anchorY,
                          boolean alignRight, float paddingX, float rowHeight, float gap,
                          float thickness, int outline, int sidebar, boolean scaleIn,
                          boolean moveIn, int screenWidth, boolean growUp, boolean uniformWidth,
                          float minimumWidth, int textAlignment) {
        this(widths, progress, anchorX, anchorY, alignRight, paddingX, rowHeight, gap, thickness,
                outline, sidebar, scaleIn, moveIn, screenWidth, growUp, uniformWidth, minimumWidth,
                textAlignment, null, null);
    }

    ModernArrayListLayout(float[] widths, float[] progress, float anchorX, float anchorY,
                          boolean alignRight, float paddingX, float rowHeight, float gap,
                          float thickness, int outline, int sidebar, boolean scaleIn,
                          boolean moveIn, int screenWidth, boolean growUp, boolean uniformWidth,
                          float minimumWidth, int textAlignment, boolean[] enabled,
                          UnaryOperator<Row> position) {
        this.progress = progress.clone();
        left = right = anchorX;
        top = bottom = anchorY;
        float y = anchorY;
        float widest = 0;
        for (int i = 0; i < widths.length; i++) {
            if (progress[i] > 0) widest = Math.max(widest, widths[i]);
        }
        for (int i = 0; i < widths.length; i++) {
            float visibility = Math.max(0, Math.min(1, progress[i]));
            if (visibility <= 0) {
                continue;
            }
            float contentWidth = Math.max(uniformWidth ? widest : widths[i], minimumWidth - paddingX * 2);
            float fullWidth = contentWidth + paddingX * 2;
            float fullLeft = alignRight ? anchorX + paddingX - fullWidth : anchorX - paddingX;
            int alignment = textAlignment == 0 ? (alignRight ? 3 : 1) : textAlignment;
            float textLeft = fullLeft + paddingX;
            if (alignment == 2) textLeft += (contentWidth - widths[i]) / 2;
            else if (alignment == 3) textLeft += contentWidth - widths[i];
            float slide = moveIn
                    ? (alignRight ? screenWidth - fullLeft + thickness
                    : -(fullLeft + fullWidth + thickness)) * (1 - visibility)
                    : 0;
            float width = fullWidth * (scaleIn ? visibility : 1);
            float edge = anchorX + (alignRight ? paddingX : -paddingX) + slide;
            float rowLeft = alignRight ? edge - width : edge;
            float rowRight = alignRight ? edge : edge + width;
            float height = rowHeight * (enabled != null && !scaleIn ? 1 : visibility);
            float rowTop = growUp ? y - height : y;
            Row row = new Row(i, rowLeft, rowTop, rowRight, rowTop + height,
                    textLeft + slide, visibility, alignRight ? rowLeft : rowRight,
                    alignRight ? rowRight : rowLeft);
            if (position != null) row = position.apply(row);
            rows.add(row);
            left = Math.min(left, row.left - thickness);
            right = Math.max(right, row.right + thickness);
            top = Math.min(top, row.top - thickness);
            bottom = Math.max(bottom, row.bottom + thickness);
            float advance = enabled == null ? height + gap * visibility : enabled[i] ? rowHeight + gap : 0;
            y += (growUp ? -1 : 1) * advance;
        }
        List<Row> visualRows = new ArrayList<>(rows);
        visualRows.sort(Comparator.comparingDouble(row -> row.top));
        for (int i = 0; i < visualRows.size(); i++) {
            Row row = visualRows.get(i);
            if (outline == 1) {
                if (sidebar != 1) vertical(row.outerEdge, row, thickness, alignRight);
                if (sidebar != 0) vertical(row.innerEdge, row, thickness, !alignRight);
            } else if (outline == 4) {
                horizontal(row.left, row.right, row.top - thickness, row.top, row.index);
            } else if (outline == 5) {
                horizontal(row.left, row.right, row.bottom, row.bottom + thickness, row.index);
            } else if (outline == 2 || outline == 3) {
                vertical(row.innerEdge, row, thickness, !alignRight);
                if (outline == 3) vertical(row.outerEdge, row, thickness, alignRight);
                Row previous = i == 0 ? null : visualRows.get(i - 1);
                if (previous == null || gap > 0) {
                    if (outline == 3) horizontal(row.left - thickness, row.right + thickness,
                            row.top - thickness, row.top, row.index);
                } else {
                    if (row.top > previous.bottom) {
                        Row bridge = new Row(previous.index, previous.left, previous.bottom,
                                previous.right, row.top, previous.textX, previous.progress,
                                previous.innerEdge, previous.outerEdge);
                        vertical(previous.innerEdge, bridge, thickness, !alignRight);
                        if (outline == 3) vertical(previous.outerEdge, bridge, thickness, alignRight);
                    }
                    join(previous.innerEdge, row.innerEdge, row.top, thickness, row.index);
                    if (outline == 3) join(previous.outerEdge, row.outerEdge,
                            row.top, thickness, row.index);
                }
                if (outline == 3 && (i == visualRows.size() - 1 || gap > 0)) {
                    horizontal(row.left - thickness, row.right + thickness,
                            row.bottom, row.bottom + thickness, row.index);
                }
            }
        }
    }

    private void vertical(float x, Row row, float width, boolean extendRight) {
        borders.add(new Border(extendRight ? x : x - width, row.top,
                extendRight ? x + width : x, row.bottom, row.index));
    }

    private void join(float previous, float current, float y, float width, int row) {
        if (previous != current) {
            horizontal(Math.min(previous, current) - width, Math.max(previous, current) + width,
                    y - width, y, row);
        }
    }

    private void horizontal(float left, float right, float top, float bottom, int row) {
        borders.add(new Border(left, top, right, bottom, row));
    }

    static final class Row {
        final int index;
        final float left;
        final float top;
        final float right;
        final float bottom;
        final float textX;
        final float progress;
        final float innerEdge;
        final float outerEdge;

        Row(int index, float left, float top, float right, float bottom, float textX,
            float progress, float innerEdge, float outerEdge) {
            this.index = index;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.textX = textX;
            this.progress = progress;
            this.innerEdge = innerEdge;
            this.outerEdge = outerEdge;
        }

        Row translate(float x, float y) {
            return new Row(index, left + x, top + y, right + x, bottom + y, textX + x,
                    progress, innerEdge + x, outerEdge + x);
        }
    }

    static final class Border {
        final float left;
        final float top;
        final float right;
        final float bottom;
        final int row;

        Border(float left, float top, float right, float bottom, int row) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.row = row;
        }
    }
}
