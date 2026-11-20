package idle.miau.utility;

import java.util.Random;

public class ClickSimulator {
    private final Random rand;

    public ClickSimulator() {
        this.rand = new Random();
    }

    public ClickSimulator(Random rand) {
        this.rand = rand;
    }

    public long nextDelay(double minCps, double maxCps, boolean simulateExhaust) {
        double lower = Math.max(1.0D, Math.min(minCps, maxCps));
        double upper = Math.max(lower, Math.max(minCps, maxCps));
        double targetCps = lower == upper
                ? lower
                : lower + rand.nextDouble() * (upper - lower);
        return nextDelay(targetCps, simulateExhaust);
    }

    public long nextDelay(double targetCps, boolean simulateExhaust) {
        double target = Math.max(1.0D, targetCps);
        int baseDelay = Math.max(1, (int) Math.round(1000.0D / target));

        int finalDelay;

        if (simulateExhaust) {
            int variation = rand.nextInt(baseDelay + 1) - (baseDelay / 2);
            finalDelay = baseDelay + variation;

            if (rand.nextInt(100) < 15) {
                if (rand.nextBoolean()) {
                    finalDelay = 25 + rand.nextInt(16);
                } else {
                    finalDelay = baseDelay + 50 + rand.nextInt(41);
                }
            }

            if (rand.nextInt(100) < 8) {
                int spikeMult = 50 + rand.nextInt(151);
                finalDelay = (finalDelay * spikeMult) / 100;
            }

            if (rand.nextInt(100) < 10) {
                finalDelay += 10 + rand.nextInt(26);
            }
        } else {
            finalDelay = baseDelay + (rand.nextInt(21) - 10);
        }

        return Math.max(33, Math.min(180, finalDelay));
    }
}
