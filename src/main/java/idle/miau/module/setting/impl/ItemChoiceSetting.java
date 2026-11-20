package idle.miau.module.setting.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

public final class ItemChoiceSetting extends SliderSetting {
    private final List<String> choices;

    public ItemChoiceSetting(String name, int defaultValue, String[] options) {
        super(name, defaultValue, options);
        choices = Collections.unmodifiableList(Arrays.asList(options.clone()));
    }

    public ItemChoiceSetting(String name, int defaultValue) {
        super(name, defaultValue, 0, 9, 1);
        choices = IntStream.rangeClosed(0, 9)
                .mapToObj(slot -> slot == 0 ? "None" : "Slot " + slot).toList();
    }

    public List<String> getChoices() {
        return choices;
    }
}
