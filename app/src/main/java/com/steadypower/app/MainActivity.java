package com.steadypower.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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

    private FrameLayout pageHost;
    private Button homeTab;
    private Button recordsTab;
    private boolean homeVisible = true;

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
    private boolean currentRunSaved = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uiTicker = new Runnable() {
        @Override public void run() {
            if (homeVisible) updateUi();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildShell());
        showHomePage();
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

    private View buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(246, 247, 249));

        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(4), dp(8), dp(6));
        nav.setBackgroundColor(Color.WHITE);

        homeTab = new Button(this);
        homeTab.setText("主页");
        homeTab.setOnClickListener(v -> showHomePage());
        recordsTab = new Button(this);
        recordsTab.setText("测试记录");
        recordsTab.setOnClickListener(v -> showRecordsPage());
        nav.addView(homeTab, navWeight());
        nav.addView(recordsTab, navWeight());
        shell.addView(nav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        shell.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop() + dp(10);
            int bottom = insets.getSystemWindowInsetBottom();
            shell.setPadding(0, top, 0, bottom);
            return insets;
        });
        shell.requestApplyInsets();
        return shell;
    }

    private void showHomePage() {
        homeVisible = true;
        setTabState(true);
        pageHost.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(246, 247, 249));
        scroll.addView(root);

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
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        exportParams.bottomMargin = dp(14);
        root.addView(exportButton, exportParams);

        LinearLayout statsCard = card();
        TextView statsTitle = text("统计", 18, Color.rgb(30, 30, 34));
        statsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        statsCard.addView(statsTitle);
        statsText = text("暂无数据", 15, Color.DKGRAY);
        statsText.setPadding(0, dp(10), 0, 0);
        statsCard.addView(statsText);
        root.addView(statsCard, matchWrapMarginBottom(14));

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
                "• 保存记录会保存本次完整逐秒数据，可在“测试记录”中点开查看曲线和直方图。\n" +
                "• 功率按 |电池电流| × 电池电压计算，属于电池端估算值。\n" +
                "• 正在充电时，电池净电流不能直接代表整机耗电，不建议测试。",
                13, Color.DKGRAY);
        noteCard.addView(note);
        root.addView(noteCard);

        pageHost.addView(scroll);
        updateUi();
    }

    private void showRecordsPage() {
        homeVisible = false;
        setTabState(false);
        pageHost.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(246, 247, 249));
        scroll.addView(root);

        TextView title = text("测试记录", 28, Color.rgb(25, 25, 28));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("点击任意记录查看完整统计、曲线和功耗分布。", 13, Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        List<RecordStore.Record> records = RecordStore.loadRecords(this);
        if (records.isEmpty()) {
            LinearLayout empty = card();
            TextView e = text("还没有保存的测试记录。", 15, Color.DKGRAY);
            empty.addView(e);
            root.addView(empty);
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            for (RecordStore.Record record : records) {
                LinearLayout item = card();
                item.setClickable(true);
                item.setFocusable(true);
                TextView itemTitle = text(record.displayTitle(), 17, Color.rgb(28, 28, 32));
                itemTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                item.addView(itemTitle);
                TextView summary = text(String.format(Locale.US,
                        "平均 %.3f W · %d:%02d · %s",
                        record.mean,
                        record.durationSec / 60,
                        record.durationSec % 60,
                        fmt.format(new Date(record.savedAt))),
                        13, Color.DKGRAY);
                summary.setPadding(0, dp(5), 0, 0);
                item.addView(summary);
                item.setOnClickListener(v -> showRecordDetail(record));
                root.addView(item, matchWrapMarginBottom(10));
            }
        }

        pageHost.addView(scroll);
    }

    private void showRecordDetail(RecordStore.Record record) {
        homeVisible = false;
        setTabState(false);
        pageHost.removeAllViews();

        List<PowerRepository.Sample> samples = RecordStore.loadSamples(this, record);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(246, 247, 249));
        scroll.addView(root);

        Button back = new Button(this);
        back.setText("← 返回测试记录");
        back.setOnClickListener(v -> showRecordsPage());
        root.addView(back, matchWrapMarginBottom(10));

        TextView title = text(record.displayTitle(), 24, Color.rgb(25, 25, 28));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView saved = text(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(record.savedAt)), 12, Color.DKGRAY);
        saved.setPadding(0, dp(4), 0, dp(16));
        root.addView(saved);

        LinearLayout statsCard = card();
        TextView statsTitle = text("统计", 18, Color.rgb(30, 30, 34));
        statsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        statsCard.addView(statsTitle);
        TextView detailStats = text(String.format(Locale.US,
                "典型功耗（中位数）  %.3f W\n" +
                "实际平均功耗        %.3f W\n" +
                "最低 / 最高         %.3f / %.3f W\n" +
                "采样数              %d\n" +
                "测试时长            %d:%02d",
                record.median, record.mean, record.min, record.max,
                record.count, record.durationSec / 60, record.durationSec % 60),
                15, Color.DKGRAY);
        detailStats.setPadding(0, dp(10), 0, 0);
        statsCard.addView(detailStats);
        root.addView(statsCard, matchWrapMarginBottom(14));

        LinearLayout curveCard = card();
        TextView curveTitle = text("原始功耗曲线", 18, Color.rgb(30, 30, 34));
        curveTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        curveCard.addView(curveTitle);
        PowerCurveView detailCurve = new PowerCurveView(this);
        detailCurve.setData(samples);
        curveCard.addView(detailCurve, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
        root.addView(curveCard, matchWrapMarginBottom(14));

        LinearLayout histCard = card();
        TextView histTitle = text("功耗直方图", 18, Color.rgb(30, 30, 34));
        histTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        histCard.addView(histTitle);
        PowerHistogramView detailHist = new PowerHistogramView(this);
        detailHist.setData(samples);
        histCard.addView(detailHist, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(270)));
        root.addView(histCard, matchWrapMarginBottom(14));

        if (samples.isEmpty()) {
            TextView missing = text("这条记录的逐秒数据文件不存在或无法读取。", 13, Color.rgb(180, 55, 45));
            missing.setPadding(0, 0, 0, dp(10));
            root.addView(missing);
        }

        Button delete = new Button(this);
        delete.setText("删除这条记录");
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("删除后，这条记录及其完整曲线数据都会被移除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    RecordStore.delete(this, record.id);
                    showRecordsPage();
                })
                .show());
        root.addView(delete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        pageHost.addView(scroll);
    }

    private void setTabState(boolean home) {
        if (homeTab == null || recordsTab == null) return;
        homeTab.setEnabled(!home);
        recordsTab.setEnabled(home);
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
        if (!homeVisible || valueText == null) return;
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
        double sum = 0.0;
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
        input.setSingleLine(true);
        int pad = dp(20);
        LinearLayout holder = new LinearLayout(this);
        holder.setPadding(pad, 0, pad, 0);
        holder.addView(input, matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("保存测试记录")
                .setMessage(String.format(Locale.US, "本次中位功耗 %.3f W", median(samples)))
                .setView(holder)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    saveCurrentRecord(name, samples);
                })
                .show();
    }

    private void saveCurrentRecord(String name, List<PowerRepository.Sample> samples) {
        try {
            RecordStore.Record record = RecordStore.save(this, name, samples);
            currentRunSaved = true;
            updateUi();
            Toast.makeText(this, "已保存：" + record.displayTitle(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
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

    private LinearLayout.LayoutParams navWeight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(50), 1f);
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

    private static final class TestStats {
        double median;
        double mean;
        double min;
        double max;
        int count;
        long durationSec;
    }
}
