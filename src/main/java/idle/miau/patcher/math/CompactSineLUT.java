package idle.miau.patcher.math;

public final class CompactSineLUT {
    private static final int[] SINE_TABLE_INT = new int[16385];
    private static float midpoint;

    private CompactSineLUT() {
    }

    public static void init(float[] sineTable) {
        for (int i = 0; i < SINE_TABLE_INT.length; i++) {
            SINE_TABLE_INT[i] = Float.floatToRawIntBits(sineTable[i]);
        }

        midpoint = sineTable[sineTable.length / 2];

        for (int i = 0; i < sineTable.length; i++) {
            if (sineTable[i] != lookup(i)) {
                throw new IllegalStateException("Sine lookup mismatch at index " + i);
            }
        }
    }

    public static float sin(float value) {
        return lookup((int) (value * 10430.378F) & 0xFFFF);
    }

    public static float cos(float value) {
        return lookup((int) (value * 10430.378F + 16384.0F) & 0xFFFF);
    }

    private static float lookup(int index) {
        if (index == 32768) {
            return midpoint;
        }

        int negative = (index & 0x8000) << 16;
        int mask = (index << 17) >> 31;
        int position = (0x8001 & mask) + (index ^ mask);
        return Float.intBitsToFloat(SINE_TABLE_INT[position & 0x7FFF] ^ negative);
    }
}
