package idle.miau.utility;

import java.util.concurrent.ThreadLocalRandom;

public final class FastRandom {
    private static final int[] STATE = seedState();

    private FastRandom() {
    }

    private static int[] seedState() {
        ThreadLocalRandom seedSource = ThreadLocalRandom.current();
        int[] state = {
                seedSource.nextInt(),
                seedSource.nextInt(),
                seedSource.nextInt(),
                seedSource.nextInt()
        };
        if ((state[0] | state[1] | state[2] | state[3]) == 0) {
            state[0] = 0x9E3779B9;
        }
        return state;
    }

    public static synchronized float nextUnitFloat() {
        int s0 = STATE[0];
        int s1 = STATE[1];
        int s2 = STATE[2];
        int s3 = STATE[3];

        int output = s0 + s3;
        int shiftedS1 = s1 << 9;
        int xor02 = s0 ^ s2;
        int xor13 = s1 ^ s3;

        STATE[1] = xor02 ^ s1;
        STATE[0] = s0 ^ xor13;
        STATE[2] = shiftedS1 ^ xor02;
        STATE[3] = Integer.rotateLeft(xor13, 11);

        return (output >>> 8) * 0x1.0p-24f;
    }

    public static synchronized boolean bernoulli(float probability) {
        float sample = nextUnitFloat();
        return probability > sample;
    }
}
