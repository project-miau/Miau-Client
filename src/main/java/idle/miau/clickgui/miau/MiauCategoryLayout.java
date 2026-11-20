package idle.miau.clickgui.miau;

import java.util.Arrays;
import java.util.Comparator;

final class MiauCategoryLayout {
    private MiauCategoryLayout() {
    }

    static float[] resolve(
            float[] x,
            float[] y,
            float[] width,
            float[] height,
            boolean[] dragging,
            float gap) {
        int size = x.length;
        if (y.length != size || width.length != size || height.length != size || dragging.length != size) {
            throw new IllegalArgumentException("Mismatched category layout arrays");
        }

        Integer[] order = new Integer[size];
        for (int index = 0; index < size; index++) {
            order[index] = index;
        }
        Arrays.sort(order, Comparator
                .comparingDouble((Integer index) -> y[index])
                .thenComparingDouble(index -> x[index]));

        float[] resolved = y.clone();
        for (int position = 0; position < order.length; position++) {
            int current = order[position];
            float resolvedY = y[current];
            if (!dragging[current]) {
                for (int previousPosition = 0; previousPosition < position; previousPosition++) {
                    int previous = order[previousPosition];
                    float previousBottom = resolved[previous] + height[previous];
                    if (y[current] >= previousBottom || !sharesColumn(
                            x[current], width[current], x[previous], width[previous])) {
                        continue;
                    }
                    resolvedY = Math.max(resolvedY, previousBottom + gap);
                }
            }
            resolved[current] = resolvedY;
        }
        return resolved;
    }

    private static boolean sharesColumn(float firstX, float firstWidth, float secondX, float secondWidth) {
        float overlap = Math.min(firstX + firstWidth, secondX + secondWidth) - Math.max(firstX, secondX);
        return overlap >= Math.min(firstWidth, secondWidth) * 0.5F;
    }
}
