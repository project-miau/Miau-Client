package idle.miau.utility;

import java.awt.Color;

public enum MiauThemes {
    AUBERGINE("Aubergine", color(170, 7, 107), color(97, 4, 95)),
    AQUA("Aqua", color(185, 250, 255), color(79, 199, 200)),
    BANANA("Banana", color(253, 236, 177), Color.WHITE),
    BLEND("Blend", color(71, 148, 253), color(71, 253, 160)),
    BLOSSOM("Blossom", color(226, 208, 249), color(49, 119, 115)),
    BUBBLEGUM("Bubblegum", color(243, 145, 216), color(152, 165, 243)),
    CANDY_CANE("Candy Cane", color(255, 0, 0), Color.WHITE),
    CHERRY("Cherry", color(187, 55, 125), color(251, 211, 233)),
    CHRISTMAS("Christmas", color(255, 64, 64), Color.WHITE, color(64, 255, 64)),
    CORAL("Coral", color(244, 168, 150), color(52, 133, 151)),
    DIGITAL_HORIZON("Digital Horizon", color(95, 195, 228), color(229, 93, 135)),
    EXPRESS("Express", color(173, 83, 137), color(60, 16, 83)),
    LIME_WATER("Lime Water", color(18, 255, 247), color(179, 255, 171)),
    LUSH("Lush", color(168, 224, 99), color(86, 171, 47)),
    HALOGEN("Halogen", color(255, 65, 108), color(255, 75, 43)),
    HYPER("Hyper", color(236, 110, 173), color(52, 148, 230)),
    MAGIC("Magic", color(74, 0, 224), color(142, 45, 226)),
    MAY("May", color(238, 79, 238), color(253, 219, 245)),
    ORANGE_JUICE("Orange Juice", color(252, 74, 26), color(247, 183, 51)),
    PASTEL("Pastel", color(243, 155, 178), color(207, 196, 243)),
    PUMPKIN("Pumpkin", color(241, 166, 98), color(255, 216, 169), color(227, 139, 42)),
    SATIN("Satin", color(215, 60, 67), color(140, 23, 39)),
    SNOWY_SKY("Snowy Sky", color(1, 171, 179), color(234, 234, 234), color(18, 232, 232)),
    STEEL_FADE("Steel Fade", color(66, 134, 244), color(55, 59, 68)),
    SUNDAE("Sundae", color(206, 74, 126), color(122, 44, 77)),
    SUNKIST("Sunkist", color(242, 201, 76), color(242, 153, 74)),
    WATER("Water", color(12, 232, 199), color(12, 163, 232)),
    LEGACY("Legacy", color(0x70CEFF), color(0x70CEFF)),
    WINTER("Winter", Color.WHITE, Color.WHITE),
    PEONY("Peony", color(226, 208, 249), color(207, 171, 255)),
    SHADOW("Shadow", color(97, 131, 255), color(206, 212, 255)),
    WOOD("Wood", color(79, 109, 81), color(170, 139, 87), color(240, 235, 206)),
    CREIDA("Creida", new Color(0xff4e5270).brighter().brighter(), new Color(0xff4e5270).darker()),
    CREIDA_TWO("Creida Two", new Color(0xff9ACAEB), new Color(0xff7FBBE6).darker()),
    GOTHIC("Gothic", color(31, 30, 30), color(196, 190, 190)),
    SEN("Rue", color(234, 118, 176), color(31, 30, 30)),
    PURPLE("Purple", color(0x524391), color(0x524391).brighter()),
    FAITHS("Faiths", color(164, 53, 144), color(100, 30, 90)),
    NORD("Nord", color(143, 188, 187), color(163, 190, 140), color(236, 239, 244)),
    RAINBOW("Rainbow", null, null);

    public static final String[] NAMES = createNames();

    private final String label;
    private final Color first;
    private final Color second;
    private final Color third;
    private final boolean triColor;

    MiauThemes(String label, Color first, Color second) {
        this(label, first, second, first, false);
    }

    MiauThemes(String label, Color first, Color second, Color third) {
        this(label, first, second, third, true);
    }

    MiauThemes(String label, Color first, Color second, Color third, boolean triColor) {
        this.label = label;
        this.first = first;
        this.second = second;
        this.third = third;
        this.triColor = triColor;
    }

    public String label() {
        return label;
    }

    public Color first() {
        return this == RAINBOW ? rainbow(0) : first;
    }

    public Color second() {
        return this == RAINBOW ? rainbow(500) : second;
    }

    public Color third() {
        return this == RAINBOW ? rainbow(1000) : third;
    }

    public Color accent(double x, double y) {
        if (this == RAINBOW) {
            return rainbow((int) ((x + y) * 10));
        }

        double blendFactor = getBlendFactor(x, y);
        if (!triColor) {
            return mix(first, second, blendFactor);
        }

        if (blendFactor <= 0.5D) {
            return mix(second, first, blendFactor * 2.0D);
        }
        return mix(third, second, (blendFactor - 0.5D) * 2.0D);
    }

    private double getBlendFactor(double x, double y) {
        return Math.sin(System.currentTimeMillis() / 600.0D + x * 0.005D + y * 0.06D) * 0.5D + 0.5D;
    }

    private static Color mix(Color first, Color second, double factor) {
        double inverseFactor = 1.0D - factor;
        int red = (int) (first.getRed() * inverseFactor + second.getRed() * factor);
        int green = (int) (first.getGreen() * inverseFactor + second.getGreen() * factor);
        int blue = (int) (first.getBlue() * inverseFactor + second.getBlue() * factor);
        return new Color(red, green, blue);
    }

    private static Color rainbow(int delay) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 10.0D) % 360.0D;
        return Color.getHSBColor((float) (rainbowState / 360.0D), 0.6F, 1.0F);
    }

    private static String[] createNames() {
        MiauThemes[] themes = values();
        String[] names = new String[themes.length];
        for (int i = 0; i < themes.length; i++) {
            names[i] = themes[i].label;
        }
        return names;
    }

    private static Color color(int red, int green, int blue) {
        return new Color(red, green, blue);
    }

    private static Color color(int rgb) {
        return new Color(rgb);
    }
}
