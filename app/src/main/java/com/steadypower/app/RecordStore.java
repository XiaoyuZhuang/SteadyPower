package com.steadypower.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class RecordStore {
    private static final String PREFS = "steadypower_records_v3";
    private static final String KEY = "records";
    private static final int MAX_RECORDS = 100;

    private RecordStore() {}

    public static final class Record {
        public long id;
        public String name;
        public long savedAt;
        public double median;
        public double mean;
        public double min;
        public double max;
        public int count;
        public long durationSec;
        public String fileName;

        public String displayTitle() {
            return String.format(Locale.US, "%s · %.3f W", name, median);
        }
    }

    public static Record save(Context context, String name, List<PowerRepository.Sample> samples) throws Exception {
        if (samples == null || samples.isEmpty()) throw new IllegalArgumentException("No samples");

        Record record = new Record();
        record.id = System.currentTimeMillis();
        record.savedAt = record.id;
        record.name = (name == null || name.trim().isEmpty()) ? "未命名测试" : name.trim();
        record.count = samples.size();
        record.durationSec = samples.get(samples.size() - 1).elapsedMs / 1000L;
        record.min = Double.POSITIVE_INFINITY;
        record.max = Double.NEGATIVE_INFINITY;

        double sum = 0.0;
        ArrayList<Double> values = new ArrayList<>(samples.size());
        for (PowerRepository.Sample sample : samples) {
            sum += sample.powerW;
            record.min = Math.min(record.min, sample.powerW);
            record.max = Math.max(record.max, sample.powerW);
            values.add(sample.powerW);
        }
        record.mean = sum / samples.size();
        Collections.sort(values);
        int n = values.size();
        record.median = ((n & 1) == 1)
                ? values.get(n / 2)
                : (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;

        File dir = new File(context.getFilesDir(), "records");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create records dir");
        record.fileName = "record_" + record.id + ".csv";
        File dataFile = new File(dir, record.fileName);
        writeSamples(dataFile, samples);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = readArray(prefs);
        array.put(toJson(record));

        while (array.length() > MAX_RECORDS) {
            JSONObject oldest = array.optJSONObject(0);
            if (oldest != null) {
                String oldFile = oldest.optString("fileName", "");
                if (!oldFile.isEmpty()) new File(dir, oldFile).delete();
            }
            JSONArray trimmed = new JSONArray();
            for (int i = 1; i < array.length(); i++) trimmed.put(array.get(i));
            array = trimmed;
        }

        prefs.edit().putString(KEY, array.toString()).apply();
        return record;
    }

    public static List<Record> loadRecords(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = readArray(prefs);
        ArrayList<Record> out = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) continue;
            Record r = fromJson(obj);
            if (r != null) out.add(r);
        }
        out.sort((a, b) -> Long.compare(b.savedAt, a.savedAt));
        return out;
    }

    public static List<PowerRepository.Sample> loadSamples(Context context, Record record) {
        ArrayList<PowerRepository.Sample> out = new ArrayList<>();
        if (record == null || record.fileName == null || record.fileName.isEmpty()) return out;
        File file = new File(new File(context.getFilesDir(), "records"), record.fileName);
        if (!file.exists()) return out;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1);
                if (p.length < 6) continue;
                try {
                    out.add(new PowerRepository.Sample(
                            Long.parseLong(p[0]),
                            Long.parseLong(p[1]),
                            Long.parseLong(p[2]),
                            Integer.parseInt(p[3]),
                            Double.parseDouble(p[4]),
                            Boolean.parseBoolean(p[5])));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static void delete(Context context, long id) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = readArray(prefs);
        JSONArray kept = new JSONArray();
        String fileName = null;
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.optJSONObject(i);
            if (obj == null) continue;
            if (obj.optLong("id", -1L) == id) {
                fileName = obj.optString("fileName", "");
            } else {
                kept.put(obj);
            }
        }
        prefs.edit().putString(KEY, kept.toString()).apply();
        if (fileName != null && !fileName.isEmpty()) {
            new File(new File(context.getFilesDir(), "records"), fileName).delete();
        }
    }

    private static void writeSamples(File file, List<PowerRepository.Sample> samples) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write("timestamp_ms,elapsed_ms,current_uA,voltage_mV,power_W,charging\n");
            for (PowerRepository.Sample s : samples) {
                writer.write(Long.toString(s.timestampMs));
                writer.write(',');
                writer.write(Long.toString(s.elapsedMs));
                writer.write(',');
                writer.write(Long.toString(s.currentUa));
                writer.write(',');
                writer.write(Integer.toString(s.voltageMv));
                writer.write(',');
                writer.write(String.format(Locale.US, "%.9f", s.powerW));
                writer.write(',');
                writer.write(Boolean.toString(s.charging));
                writer.write('\n');
            }
        }
    }

    private static JSONArray readArray(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static JSONObject toJson(Record r) throws Exception {
        JSONObject obj = new JSONObject();
        obj.put("id", r.id);
        obj.put("name", r.name);
        obj.put("savedAt", r.savedAt);
        obj.put("median", r.median);
        obj.put("mean", r.mean);
        obj.put("min", r.min);
        obj.put("max", r.max);
        obj.put("count", r.count);
        obj.put("durationSec", r.durationSec);
        obj.put("fileName", r.fileName);
        return obj;
    }

    private static Record fromJson(JSONObject obj) {
        try {
            Record r = new Record();
            r.id = obj.getLong("id");
            r.name = obj.optString("name", "未命名测试");
            r.savedAt = obj.optLong("savedAt", r.id);
            r.median = obj.optDouble("median", 0.0);
            r.mean = obj.optDouble("mean", 0.0);
            r.min = obj.optDouble("min", 0.0);
            r.max = obj.optDouble("max", 0.0);
            r.count = obj.optInt("count", 0);
            r.durationSec = obj.optLong("durationSec", 0L);
            r.fileName = obj.optString("fileName", "");
            return r;
        } catch (Exception ignored) {
            return null;
        }
    }
}
