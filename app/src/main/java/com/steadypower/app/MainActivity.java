package com.steadypower.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 20;
    private static final int REQ_EXPORT = 21;
    private static final String PREFS_HISTORY = "steadypower_history";
    private static final String KEY_HISTORY = "records";

    private TextView valueText;
    private TextView valueLabel;
    private TextView statusText;
    private TextView statsText;
    private Button startButton;
    private Button stopButton;
    private Button saveButton;
    private Button exportButton;
    private PowerCurveView curveView;
    private PowerHistogramView histogramView;
    private LinearLayout historyList;
    private boolean currentRunSaved = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uiTicker = new Runnable() {
        @Override public void run() {
            updateUi();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        refreshHistory();
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(uiTicker);
        handler.post(uiTicker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(uiTicker);
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(32));
        root.setBackgroundColor(Color.rgb(246, 247, 249));
        scroll.addView(root);

        // Android 15+ 强制 edge-to-edge 后内容可能进入状态栏区域；使用系统 inset 留出真实间距。
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = dp(18) + insets.getSystemWindowInsetTop();
            int bottom = dp(32) + insets.getSystemWindowInsetBottom();
            root.setPadding(dp(18), top, dp(18), bottom);
            return insets;
        });
        scroll.requestApplyInsets();

        TextView title = text("SteadyPower", 28, Color.rgb(25, 25, 28));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("1 Hz 电池端功耗记录 · 不自动删峰 · 不自动判稳态", 13, Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout hero = card();
        valueText = text("-- W", 42, Color.rgb(20, 70, 145));
        valueText.setGravity(Gravity.CENTER);
        valueText.setTypeface(null, android.graphics.Typeface.BOLD);
        hero.addView(valueText, matchWrap());
        valueLabel = text("当前功耗", 14, Color.DKGRAY);
        valueLabel.setGravity(Gravity.CENTER);
        hero.addView(valueLabel, matchWrap());
        statusText = text("尚未开始测试", 13, Color.DKGRAY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(8), 0, 0);
        hero.addView(statusText, matchWrap());
        root.addView(hero, matchWrapMarginBottom(14));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        startButton = new Button(this);
        startButton.setText("开始测试");
        startButton.setOnClickListener(v -> startTest());
        stopButton = new Button(this);
        stopButton.setText("停止");
        stopButton.setOnClickListener(v -> stopTest());
        saveButton = new Button(this);
        saveButton.setText("保存记录");
        saveButton.setOnClickListener(v -> showSaveDialog());
        buttons.addView(startButton, weight());
        buttons.addView(stopButton, weight());
        buttons.addView(saveButton, weight());
        root.addView(buttons, matchWrapMarginBottom(6));

        exportButton = new Button(this);
        exportButton.setText("导出当前测试 CSV");
        exportButton.setOnClickListener(v -> exportCsv());
        root.addView(exportButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        ((LinearLayout.LayoutParams) exportButton.getLayoutParams()).bottomMargin = dp(14);

        LinearLayout statsCard = card();
        TextView statsTitle = text("统计", 18, Color.rgb(30, 30, 34));
        statsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        statsCard.addView(statsTitle);
        statsText = text("暂无数据", 15, Color.DKGRAY);
        statsText.setPadding(0, dp(10), 0, 0);
        statsCard.addView(statsText);
        root.addView(statsCard, matchWrapMarginBottom(14));

        LinearLayout historyCard = card();
        TextView historyTitle = text("测试记录", 18, Color.rgb(30, 30, 34));
        historyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        historyCard.addView(historyTitle);
        TextView historyNote = text("测试停止后点“保存记录”，输入例如“1000Hz，抖音测试”；中位功耗会自动追加到标题。", 12, Color.DKGRAY);
        historyNote.setPadding(0, dp(4), 0, dp(8));
        historyCard.addView(historyNote);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyCard.addView(historyList, matchWrap());
        root.addView(historyCard, matchWrapMarginBottom(14));

        LinearLayout curveCard = card();
        TextView curveTitle = text("原始功耗曲线", 18, Color.rgb(30, 30, 34));
        curveTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        curveCard.addView(curveTitle);
        curveView = new PowerCurveView(this);
        curveCard.addView(curveView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
        root.addView(curveCard, matchWrapMarginBottom(14));

        LinearLayout histCard = card();
        TextView histTitle = text("功耗直方图", 18, Color.rgb(30, 30, 34));
        histTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        histCard.addView(histTitle);
        TextView histNote = text("固定每 0.05 W 一个区间；最高柱就是最常出现的功耗范围。", 12, Color.DKGRAY);
        histNote.setPadding(0, dp(4), 0, dp(4));
        histCard.addView(histNote);
        histogramView = new PowerHistogramView(this);
        histCard.addView(histogramView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(270)));
        root.addView(histCard, matchWrapMarginBottom(14));

        LinearLayout noteCard = card();
        TextView note = text(
                "读数说明\n" +
                "• 典型功耗 = 全部有效采样的中位数，对偶发尖峰不敏感。\n" +
                "• 实际平均 = 全部有效采样的算术平均，保留真实尖峰。\n" +
                "• 保存记录只保存本次摘要，原始逐秒数据仍可单独导出 CSV。\n" +
                "• 功率按 |电池电流| × 电池电压计算，属于电池端估算值。\n" +
                "• 正在充电时，电池净电流不能直接代表整机耗电，不建议测试。",
                13, Color.DKGRAY);
        noteCard.addView(note);
        root.addView(noteCard);

        return scroll;
    }

    private void startTest() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
        Intent intent = new Intent(this, PowerSamplingService.class);
        intent.setAction(PowerSamplingService.ACTION_START);
        try {
            currentRunSaved = false;
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            Toast.makeText(this, "已开始，直接切到要测试的软件即可", Toast.LENGTH_SHORT).show();
            handler.postDelayed(this::updateUi, 250L);
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopTest() {
        stopService(new Intent(this, PowerSamplingService.class));
        PowerRepository.stop();
        updateUi();
    }

    private void updateUi() {
        List<PowerRepository.Sample> samples = PowerRepository.snapshot();
        boolean running = PowerRepository.isRunning();
        startButton.setEnabled(!running);
        stopButton.setEnabled(running);
        saveButton.setEnabled(!running && !samples.isEmpty() && !currentRunSaved);
        exportButton.setEnabled(!samples.isEmpty());

        PowerRepository.Sample latest = samples.isEmpty() ? null : samples.get(samples.size() - 1);
        if (running) {
            valueLabel.setText("当前功耗");
            valueText.setText(latest == null ? "-- W" : String.format(Locale.US, "%.3f W", latest.powerW));
        } else {
            valueLabel.setText("典型功耗（中位数）");
            valueText.setText(samples.isEmpty() ? "-- W" : String.format(Locale.US, "%.3f W", median(samples)));
        }

        String error = PowerRepository.getLastError();
        if (!error.isEmpty()) {
            statusText.setText(error);
            statusText.setTextColor(Color.rgb(180, 55, 45));
        } else if (latest != null && latest.charging) {
            statusText.setText("检测到正在充电：不建议把当前结果当作整机功耗");
            statusText.setTextColor(Color.rgb(185, 90, 20));
        } else if (running) {
            statusText.setText("正在后台 1 Hz 采样，可直接切换到目标软件");
            statusText.setTextColor(Color.rgb(45, 115, 55));
        } else if (!samples.isEmpty()) {
            statusText.setText(currentRunSaved ? "测试已停止 · 已保存记录" : "测试已停止");
            statusText.setTextColor(Color.DKGRAY);
        } else {
            statusText.setText("尚未开始测试");
            statusText.setTextColor(Color.DKGRAY);
        }

        statsText.setText(buildStats(samples));
        curveView.setData(samples);
        histogramView.setData(samples);
    }

    private String buildStats(List<PowerRepository.Sample> samples) {
        if (samples.isEmpty()) return "暂无数据";
        TestStats s = calculateStats(samples);
        return String.format(Locale.US,
                "典型功耗（中位数）  %.3f W\n" +
                "实际平均功耗        %.3f W\n" +
                "最低 / 最高         %.3f / %.3f W\n" +
                "采样数              %d\n" +
                "测试时长            %d:%02d",
                s.median, s.mean, s.min, s.max, s.count, s.durationSec / 60, s.durationSec % 60);
    }

    private TestStats calculateStats(List<PowerRepository.Sample> samples) {
        TestStats out = new TestStats();
        if (samples.isEmpty()) return out;
        double sum = 0;
        out.min = Double.POSITIVE_INFINITY;
        out.max = Double.NEGATIVE_INFINITY;
        for (PowerRepository.Sample sample : samples) {
            sum += sample.powerW;
            out.min = Math.min(out.min, sample.powerW);
            out.max = Math.max(out.max, sample.powerW);
        }
        out.count = samples.size();
        out.mean = sum / out.count;
        out.median = median(samples);
        out.durationSec = samples.get(samples.size() - 1).elapsedMs / 1000L;
        return out;
    }

    private double median(List<PowerRepository.Sample> samples) {
        ArrayList<Double> values = new ArrayList<>(samples.size());
        for (PowerRepository.Sample s : samples) values.add(s.powerW);
        Collections.sort(values);
        int n = values.size();
        if ((n & 1) == 1) return values.get(n / 2);
        return (values.get(n / 2 - 1) + values.get(n / 2)) / 2.0;
    }

    private void showSaveDialog() {
        List<PowerRepository.Sample> samples = PowerRepository.snapshot();
        if (samples.isEmpty() || PowerRepository.isRunning()) return;

        EditText input = new EditText(this);
        input.setHint("例如：1000Hz，抖音测试");
        input.setSingleLine(true);
        int pad = dp(20);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(pad, 0, pad, 0);
        holder.addView(input, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("保存测试记录")
                .setMessage(String.format(Locale.US, "本次中位功耗 %.3f W\n功耗数值会自动追加到标题。", median(samples)))
                .setView(holder)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "未命名测试";
                    saveCurrentRecord(name, samples);
                })
                .show();
    }

    private void saveCurrentRecord(String name, List<PowerRepository.Sample> samples) {
        TestStats stats = calculateStats(samples);
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE);
            JSONArray array;
            try {
                array = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            } catch (Exception ignored) {
                array = new JSONArray();
            }

            JSONObject record = new JSONObject();
            record.put("id", System.currentTimeMillis());
            record.put("name", name);
            record.put("savedAt", System.currentTimeMillis());
            record.put("median", stats.median);
            record.put("mean", stats.mean);
            record.put("min", stats.min);
            record.put("max", stats.max);
            record.put("count", stats.count);
            record.put("durationSec", stats.durationSec);
            array.put(record);

            // 摘要很小，但仍限制最多 100 条，避免无限增长。
            if (array.length() > 100) {
                JSONArray trimmed = new JSONArray();
                for (int i = array.length() - 100; i < array.length(); i++) trimmed.put(array.get(i));
                array = trimmed;
            }

            prefs.edit().putString(KEY_HISTORY, array.toString()).apply();
            currentRunSaved = true;
            refreshHistory();
            updateUi();
            Toast.makeText(this,
                    String.format(Locale.US, "已保存：%s · %.3f W", name, stats.median),
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存记录失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshHistory() {
        if (historyList == null) return;
        historyList.removeAllViews();
        List<HistoryRecord> records = loadHistory();
        if (records.isEmpty()) {
            TextView empty = text("暂无保存记录", 13, Color.GRAY);
            empty.setPadding(0, dp(6), 0, dp(4));
            historyList.addView(empty);
            return;
        }

        for (HistoryRecord r : records) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(7), 0, dp(7));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView name = text(String.format(Locale.US, "%s · %.3f W", r.name, r.median),
                    15, Color.rgb(25, 25, 28));
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            info.addView(name);
            String time = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(r.savedAt));
            TextView detail = text(String.format(Locale.US,
                    "平均 %.3f W · %d:%02d · %s",
                    r.mean, r.durationSec / 60, r.durationSec % 60, time), 12, Color.DKGRAY);
            detail.setPadding(0, dp(3), 0, 0);
            info.addView(detail);
            row.addView(info, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button delete = new Button(this);
            delete.setText("删除");
            delete.setTextSize(11);
            delete.setOnClickListener(v -> confirmDeleteRecord(r.id, r.name));
            row.addView(delete, new LinearLayout.LayoutParams(dp(72), dp(42)));
            historyList.addView(row, matchWrap());

            View divider = new View(this);
            divider.setBackgroundColor(Color.rgb(235, 236, 239));
            historyList.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private List<HistoryRecord> loadHistory() {
        ArrayList<HistoryRecord> out = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE);
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            for (int i = array.length() - 1; i >= 0; i--) {
                JSONObject o = array.getJSONObject(i);
                HistoryRecord r = new HistoryRecord();
                r.id = o.optLong("id", 0L);
                r.name = o.optString("name", "未命名测试");
                r.savedAt = o.optLong("savedAt", r.id);
                r.median = o.optDouble("median", 0.0);
                r.mean = o.optDouble("mean", 0.0);
                r.durationSec = o.optLong("durationSec", 0L);
                out.add(r);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void confirmDeleteRecord(long id, String name) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("确定删除“" + name + "”吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> deleteRecord(id))
                .show();
    }

    private void deleteRecord(long id) {
        SharedPreferences prefs = getSharedPreferences(PREFS_HISTORY, Context.MODE_PRIVATE);
        try {
            JSONArray old = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            JSONArray next = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject o = old.getJSONObject(i);
                if (o.optLong("id", -1L) != id) next.put(o);
            }
            prefs.edit().putString(KEY_HISTORY, next.toString()).apply();
            refreshHistory();
        } catch (Exception e) {
            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void exportCsv() {
        if (PowerRepository.snapshot().isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        String name = "SteadyPower_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        intent.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_EXPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        List<PowerRepository.Sample> samples = PowerRepository.snapshot();
        StringBuilder sb = new StringBuilder();
        sb.append("timestamp_ms,elapsed_s,current_mA,voltage_V,power_W,charging\n");
        for (PowerRepository.Sample s : samples) {
            sb.append(s.timestampMs).append(',')
                    .append(String.format(Locale.US, "%.3f", s.elapsedMs / 1000.0)).append(',')
                    .append(String.format(Locale.US, "%.3f", s.currentUa / 1000.0)).append(',')
                    .append(String.format(Locale.US, "%.3f", s.voltageMv / 1000.0)).append(',')
                    .append(String.format(Locale.US, "%.6f", s.powerW)).append(',')
                    .append(s.charging).append('\n');
        }
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("No output stream");
            os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "CSV 已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(228, 229, 232));
        l.setBackground(bg);
        return l;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapMarginBottom(int bottomDp) {
        LinearLayout.LayoutParams p = matchWrap();
        p.bottomMargin = dp(bottomDp);
        return p;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class TestStats {
        double median;
        double mean;
        double min;
        double max;
        int count;
        long durationSec;
    }

    private static class HistoryRecord {
        long id;
        String name;
        long savedAt;
        double median;
        double mean;
        long durationSec;
    }
}
