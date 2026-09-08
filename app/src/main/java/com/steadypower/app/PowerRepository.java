package com.steadypower.app;

import java.util.ArrayList;
import java.util.List;

public final class PowerRepository {
    private PowerRepository() {}

    public static final class Sample {
        public final long timestampMs;
        public final long elapsedMs;
        public final long currentUa;
        public final int voltageMv;
        public final double powerW;
        public final boolean charging;

        public Sample(long timestampMs, long elapsedMs, long currentUa, int voltageMv,
                      double powerW, boolean charging) {
            this.timestampMs = timestampMs;
            this.elapsedMs = elapsedMs;
            this.currentUa = currentUa;
            this.voltageMv = voltageMv;
            this.powerW = powerW;
            this.charging = charging;
        }
    }

    private static final List<Sample> samples = new ArrayList<>();
    private static boolean running = false;
    private static long startElapsedRealtime = 0L;
    private static String lastError = "";

    public static synchronized void start(long elapsedRealtime) {
        samples.clear();
        running = true;
        startElapsedRealtime = elapsedRealtime;
        lastError = "";
    }

    public static synchronized void stop() {
        running = false;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    public static synchronized long getStartElapsedRealtime() {
        return startElapsedRealtime;
    }

    public static synchronized void add(Sample sample) {
        samples.add(sample);
    }

    public static synchronized List<Sample> snapshot() {
        return new ArrayList<>(samples);
    }

    public static synchronized Sample latest() {
        return samples.isEmpty() ? null : samples.get(samples.size() - 1);
    }

    public static synchronized void setLastError(String error) {
        lastError = error == null ? "" : error;
    }

    public static synchronized String getLastError() {
        return lastError;
    }
}
