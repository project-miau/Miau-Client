package idle.miau.clickgui.modern;

import idle.miau.clickgui.GuiCategories;

final class MiauLayout {
    static final float COLUMN_WIDTH = 110;
    static final float COLUMN_GAP = 10;
    static final float WIDTH = (COLUMN_WIDTH + COLUMN_GAP) * GuiCategories.modules().size() - COLUMN_GAP;
    static final float COLUMN_TOP = 44;
    static final float HEADER_HEIGHT = 20;
    static final float LIST_TOP = COLUMN_TOP + HEADER_HEIGHT + 4;
    static final float ROW_HEIGHT = 24;
    static final float ROW_GAP = 2;
    static final float TAB_WIDTH = 92;
    static final float TAB_TOP = 8;
    static final float TAB_HEIGHT = 24;

    private MiauLayout() {}

    static float left(float screenWidth) { return (screenWidth - WIDTH) / 2; }

    static float tabLeft(float screenWidth) { return (screenWidth - TAB_WIDTH * 3) / 2; }
}
