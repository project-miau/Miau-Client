package idle.miau.clickgui.panel;

import java.io.InputStream;

public final class Platform {
    private Platform() {
    }

    public static InputStream resource(String path) {
        return Platform.class.getResourceAsStream(path);
    }
}
