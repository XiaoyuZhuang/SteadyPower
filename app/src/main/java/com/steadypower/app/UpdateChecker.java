package com.steadypower.app;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker {
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/XiaoyuZhuang/SteadyPower/releases/latest";

    private UpdateChecker() {}

    public interface Callback {
        void onResult(Result result);
        void onError(Exception error);
    }

    public static final class Result {
        public final String latestVersion;
        public final String releaseUrl;
        public final boolean hasUpdate;

        Result(String latestVersion, String releaseUrl, boolean hasUpdate) {
            this.latestVersion = latestVersion;
            this.releaseUrl = releaseUrl;
            this.hasUpdate = hasUpdate;
        }
    }

    public static void check(String currentVersion, Callback callback) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(LATEST_RELEASE_API).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "SteadyPower-Android");

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new IllegalStateException("GitHub HTTP " + code);
                }

                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }

                JSONObject json = new JSONObject(body.toString());
                String tag = json.optString("tag_name", "").trim();
                String releaseUrl = json.optString("html_url",
                        "https://github.com/XiaoyuZhuang/SteadyPower/releases/latest");
                if (tag.isEmpty()) throw new IllegalStateException("Latest release has no tag");

                String latest = stripVersionPrefix(tag);
                boolean hasUpdate = compareVersions(latest, currentVersion) > 0;
                callback.onResult(new Result(latest, releaseUrl, hasUpdate));
            } catch (Exception e) {
                callback.onError(e);
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "SteadyPower-update-check").start();
    }

    static int compareVersions(String left, String right) {
        String[] a = stripVersionPrefix(left).split("\\.");
        String[] b = stripVersionPrefix(right).split("\\.");
        int max = Math.max(a.length, b.length);
        for (int i = 0; i < max; i++) {
            int av = i < a.length ? leadingNumber(a[i]) : 0;
            int bv = i < b.length ? leadingNumber(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static int leadingNumber(String value) {
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stripVersionPrefix(String version) {
        if (version == null) return "0";
        String out = version.trim();
        if (out.startsWith("v") || out.startsWith("V")) out = out.substring(1);
        int dash = out.indexOf('-');
        if (dash >= 0) out = out.substring(0, dash);
        int plus = out.indexOf('+');
        if (plus >= 0) out = out.substring(0, plus);
        return out;
    }
}
