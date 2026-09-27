package com.steadypower.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
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

    private static final String PREFS = "steadypower_settings";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_HAPTICS = "haptics";
    private static final String KEY_COUNTDOWN_MINUTES = "countdown_minutes";

    private static final int PAGE_HOME = 0;
    private static final int PAGE_RECORDS = 1;
    private static final int PAGE_DETAIL = 2;
    private static final int PAGE_SETTINGS = 3;

    private FrameLayout pageHost;
    private Button homeTab;
    private Button recordsTab;
    private Button settingsTab;
    private boolean homeVisible = true;
    private int currentPage = PAGE_HOME;
    private boolean darkMode;
    private boolean hapticsEnabled = true;
    private long lastAutoSavedRecordId = 0L;

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
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        darkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        hapticsEnabled = prefs.getBoolean(KEY_HAPTICS, true);
        setTheme(darkMode
                ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);

        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(backgroundColor());
        getWindow().setNavigationBarColor(cardColor());
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
        shell.setBackgroundColor(backgroundColor());

        pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(4), dp(8), dp(6));
        nav.setBackgroundColor(cardColor());

        homeTab = new Button(this);
        homeTab.setText("主页");
        setHapticClick(homeTab, v -> showHomePage());
        recordsTab = new Button(this);
        recordsTab.setText("测试记录");
        setHapticClick(recordsTab, v -> showRecordsPage());
        settingsTab = new Button(this);
        settingsTab.setText("设置");
        setHapticClick(settingsTab, v -> showSettingsPage());
        nav.addView(homeTab, navWeight());
        nav.addView(recordsTab, navWeight());
        nav.addView(settingsTab, navWeight());
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
        currentPage = PAGE_HOME;
        setTabState(PAGE_HOME);
        pageHost.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        root.setBackgroundColor(backgroundColor());
        scroll.addView(root);

        TextView title = text("SteadyPower", 28, primaryTextColor());
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("1 Hz 电池端功耗记录 · 不自动删峰 · 不自动判稳态", 13, secondaryTextColor());
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout hero = card();
        valueText = text("-- W", 42, accentColor());
        valueText.setGravity(Gravity.CENTER);
        valueText.setTypeface(null, android.graphics.Typeface.BOLD);
        hero.addView(valueText, matchWrap());
        valueLabel = text("当前功耗", 14, secondaryTextColor());
        valueLabel.setGravity(Gravity.CENTER);
        hero.addView(valueLabel, matchWrap());
        statusText = text("尚未开始测试", 13, secondaryTextColor());
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(8), 0, 0);
        hero.addView(statusText, matchWrap());
        root.addView(hero, matchWrapMarginBottom(14));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        startButton = new Button(this);
        startButton.setText("开始测试");
        setHapticClick(startButton, v -> startTest());
        stopButton = new Button(this);
        stopButton.setText("停止");
        setHapticClick(stopButton, v -> stopTest());
        saveButton = new Button(this);
        saveButton.setText("保存记录");
        setHapticClick(saveButton, v -> showSaveDialog());
        buttons.addView(startButton, weight());
        buttons.addView(stopButton, weight());
        buttons.addView(saveButton, weight());
        root.addView(buttons, matchWrapMarginBottom(6));

        exportButton = new Button(this);
        exportButton.setText("导出当前测试 CSV");
        setHapticClick(exportButton, v -> exportCsv());
        LinearLayout.LayoutParams exportParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        exportParams.bottomMargin = dp(14);
        root.addView(exportButton, exportParams);

        LinearLayout statsCard = card();
        TextView statsTitle = text("统计", 18, primaryTextColor());
        statsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        statsCard.addView(statsTitle);
        statsText = text("暂无数据", 15, secondaryTextColor());
        statsText.setPadding(0, dp(10), 0, 0);
        statsCard.addView(statsText);
        root.addView(statsCard, matchWrapMarginBottom(14));

        LinearLayout curveCard = card();
        TextView curveTitle = text("原始功耗曲线", 18, primaryTextColor());
        curveTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        curveCard.addView(curveTitle);
        curveView = new PowerCurveView(this);
        curveView.setDarkMode(darkMode);
        curveCard.addView(curveView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
        root.addView(curveCard, matchWrapMarginBottom(14));

        LinearLayout histCard = card();
        TextView histTitle = text("功耗直方图", 18, primaryTextColor());
        histTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        histCard.addView(histTitle);
        TextView histNote = text("固定每 0.05 W 一个区间；最高柱就是最常出现的功耗范围。", 12, secondaryTextColor());
        histNote.setPadding(0, dp(4), 0, dp(4));
        histCard.addView(histNote);
        histogramView = new PowerHistogramView(this);
        histogramView.setDarkMode(darkMode);
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
                13, secondaryTextColor());
        noteCard.addView(note);
        root.addView(noteCard);

        pageHost.addView(scroll);
        updateUi();
    }

    private void showRecordsPage() {
        homeVisible = false;
        currentPage = PAGE_RECORDS;
        setTabState(PAGE_RECORDS);
        pageHost.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        root.setBackgroundColor(backgroundColor());
        scroll.addView(root);

        TextView title = text("测试记录", 28, primaryTextColor());
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("点击任意记录查看完整统计、曲线和功耗分布。", 13, secondaryTextColor());
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        List<RecordStore.Record> records = RecordStore.loadRecords(this);
        if (records.isEmpty()) {
            LinearLayout empty = card();
            TextView e = text("还没有保存的测试记录。", 15, secondaryTextColor());
            empty.addView(e);
            root.addView(empty);
        } else {
            SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            for (RecordStore.Record record : records) {
                LinearLayout item = card();
                item.setClickable(true);
                item.setFocusable(true);
                TextView itemTitle = text(record.displayTitle(), 17, primaryTextColor());
                itemTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                item.addView(itemTitle);
                TextView summary = text(String.format(Locale.US,
                        "平均 %.3f W · %d:%02d · %s",
                        record.mean,
                        record.durationSec / 60,
                        record.durationSec % 60,
                        fmt.format(new Date(record.savedAt))),
                        13, secondaryTextColor());
                summary.setPadding(0, dp(5), 0, 0);
                item.addView(summary);
                setHapticClick(item, v -> showRecordDetail(record));
                root.addView(item, matchWrapMarginBottom(10));
            }
        }

        pageHost.addView(scroll);
    }

    private void showRecordDetail(RecordStore.Record record) {
        homeVisible = false;
        currentPage = PAGE_DETAIL;
        setTabState(PAGE_RECORDS);
        pageHost.removeAllViews();

        List<PowerRepository.Sample> samples = RecordStore.loadSamples(this, record);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        root.setBackgroundColor(backgroundColor());
        scroll.addView(root);

        Button back = new Button(this);
        back.setText("← 返回测试记录");
        setHapticClick(back, v -> showRecordsPage());
        root.addView(back, matchWrapMarginBottom(10));

        TextView title = text(record.displayTitle(), 24, primaryTextColor());
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView saved = text(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(record.savedAt)), 12, secondaryTextColor());
        saved.setPadding(0, dp(4), 0, dp(16));
        root.addView(saved);

        LinearLayout statsCard = card();
        TextView statsTitle = text("统计", 18, primaryTextColor());
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
                15, secondaryTextColor());
        detailStats.setPadding(0, dp(10), 0, 0);
        statsCard.addView(detailStats);
        root.addView(statsCard, matchWrapMarginBottom(14));

        LinearLayout curveCard = card();
        TextView curveTitle = text("原始功耗曲线", 18, primaryTextColor());
        curveTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        curveCard.addView(curveTitle);
        PowerCurveView detailCurve = new PowerCurveView(this);
        detailCurve.setDarkMode(darkMode);
        detailCurve.setData(samples);
        curveCard.addView(detailCurve, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(240)));
        root.addView(curveCard, matchWrapMarginBottom(14));

        LinearLayout histCard = card();
        TextView histTitle = text("功耗直方图", 18, primaryTextColor());
        histTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        histCard.addView(histTitle);
        PowerHistogramView detailHist = new PowerHistogramView(this);
        detailHist.setDarkMode(darkMode);
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
        setHapticClick(delete, v -> new AlertDialog.Builder(this)
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

    private void setTabState(int selectedPage) {
        if (homeTab == null || recordsTab == null || settingsTab == null) return;
        homeTab.setEnabled(selectedPage != PAGE_HOME);
        recordsTab.setEnabled(selectedPage != PAGE_RECORDS);
        settingsTab.setEnabled(selectedPage != PAGE_SETTINGS);
    }

    private void showSettingsPage() {
        homeVisible = false;
        currentPage = PAGE_SETTINGS;
        setTabState(PAGE_SETTINGS);
        pageHost.removeAllViews();

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(28));
        root.setBackgroundColor(backgroundColor());
        scroll.addView(root);

        TextView title = text("设置", 28, primaryTextColor());
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView subtitle = text("外观、震动、倒计时测试与版本更新。", 13, secondaryTextColor());
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout basicCard = card();
        TextView basicTitle = text("基础设置", 18, primaryTextColor());
        basicTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        basicCard.addView(basicTitle);

        Switch darkSwitch = new Switch(this);
        darkSwitch.setText("夜间黑暗模式");
        darkSwitch.setTextColor(primaryTextColor());
        darkSwitch.setChecked(darkMode);
        darkSwitch.setPadding(0, dp(8), 0, dp(4));
        darkSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked == darkMode) return;
            haptic(buttonView);
            prefs.edit().putBoolean(KEY_DARK_MODE, checked).apply();
            recreate();
        });
        basicCard.addView(darkSwitch, matchWrap());

        Switch hapticSwitch = new Switch(this);
        hapticSwitch.setText("点选震动反馈");
        hapticSwitch.setTextColor(primaryTextColor());
        hapticSwitch.setChecked(hapticsEnabled);
        hapticSwitch.setPadding(0, dp(4), 0, 0);
        hapticSwitch.setOnCheckedChangeListener((buttonView, checked) -> {
            if (checked == hapticsEnabled) return;
            if (hapticsEnabled) buttonView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
            hapticsEnabled = checked;
            prefs.edit().putBoolean(KEY_HAPTICS, checked).apply();
        });
        basicCard.addView(hapticSwitch, matchWrap());
        root.addView(basicCard, matchWrapMarginBottom(14));

        LinearLayout timerCard = card();
        TextView timerTitle = text("倒计时测试", 18, primaryTextColor());
        timerTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        timerCard.addView(timerTitle);
        TextView timerNote = text(
                "默认 3 分钟。开始后可直接切到循环视频；到点会自动停止测试并保存记录。",
                13, secondaryTextColor());
        timerNote.setPadding(0, dp(6), 0, dp(10));
        timerCard.addView(timerNote);

        EditText minutesInput = new EditText(this);
        minutesInput.setSingleLine(true);
        minutesInput.setHint("分钟");
        minutesInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        minutesInput.setText(String.valueOf(prefs.getInt(KEY_COUNTDOWN_MINUTES, 3)));
        timerCard.addView(minutesInput, matchWrap());

        Button timedStart = new Button(this);
        timedStart.setText("开始倒计时测试");
        setHapticClick(timedStart, v -> startTimedTest(minutesInput));
        timerCard.addView(timedStart, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(timerCard, matchWrapMarginBottom(14));

        LinearLayout updateCard = card();
        TextView updateTitle = text("检查更新", 18, primaryTextColor());
        updateTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        updateCard.addView(updateTitle);
        TextView versionText = text("当前版本 v" + getCurrentVersionName()
                        + " · 仅通过 GitHub Releases 检查与下载",
                13, secondaryTextColor());
        versionText.setPadding(0, dp(6), 0, dp(10));
        updateCard.addView(versionText);

        Button updateButton = new Button(this);
        updateButton.setText("检查 GitHub 更新");
        setHapticClick(updateButton, v -> checkForUpdates(updateButton));
        updateCard.addView(updateButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(updateCard);

        pageHost.addView(scroll);
    }

    private void startTest() {
        startSampling(0L, null);
    }

    private void startTimedTest(EditText minutesInput) {
        if (PowerRepository.isRunning()) {
            Toast.makeText(this, "已有测试正在进行，请先停止当前测试", Toast.LENGTH_SHORT).show();
            return;
        }

        int minutes;
        try {
            minutes = Integer.parseInt(minutesInput.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "请输入 1–120 分钟的整数", Toast.LENGTH_SHORT).show();
            return;
        }

        if (minutes < 1 || minutes > 120) {
            Toast.makeText(this, "倒计时时间支持 1–120 分钟", Toast.LENGTH_SHORT).show();
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putInt(KEY_COUNTDOWN_MINUTES, minutes).apply();
        long durationMs = minutes * 60_000L;
        startSampling(durationMs, "倒计时测试 " + minutes + " 分钟");
        if (PowerRepository.isRunning()) showHomePage();
    }

    private void startSampling(long autoStopMs, String autoSaveName) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
        Intent intent = new Intent(this, PowerSamplingService.class);
        intent.setAction(PowerSamplingService.ACTION_START);
        if (autoStopMs > 0L) {
            intent.putExtra(PowerSamplingService.EXTRA_AUTO_STOP_MS, autoStopMs);
            intent.putExtra(PowerSamplingService.EXTRA_AUTO_SAVE_NAME, autoSaveName);
        }
        try {
            currentRunSaved = false;
            lastAutoSavedRecordId = 0L;
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            Toast.makeText(this,
                    autoStopMs > 0L
                            ? "倒计时测试已开始，到点会自动停止并保存"
                            : "已开始，直接切到要测试的软件即可",
                    Toast.LENGTH_SHORT).show();
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
        long autoSavedRecordId = PowerRepository.getAutoSavedRecordId();
        if (autoSavedRecordId != 0L) {
            currentRunSaved = true;
            lastAutoSavedRecordId = autoSavedRecordId;
        }
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
        long autoStopAt = PowerRepository.getAutoStopAtElapsedRealtime();
        String countdown = autoStopAt > 0L ? formatCountdown(autoStopAt) : "";
        if (!error.isEmpty()) {
            statusText.setText(error);
            statusText.setTextColor(Color.rgb(210, 85, 75));
        } else if (latest != null && latest.charging) {
            statusText.setText("检测到正在充电：不建议把当前结果当作整机功耗"
                    + (countdown.isEmpty() ? "" : "\n倒计时剩余 " + countdown));
            statusText.setTextColor(Color.rgb(220, 145, 65));
        } else if (running) {
            statusText.setText(countdown.isEmpty()
                    ? "正在后台 1 Hz 采样，可直接切换到目标软件"
                    : "倒计时测试中 · 剩余 " + countdown + " · 可切换到目标软件");
            statusText.setTextColor(Color.rgb(75, 155, 90));
        } else if (autoSavedRecordId != 0L) {
            String savedTitle = PowerRepository.getAutoSavedRecordTitle();
            statusText.setText(savedTitle.isEmpty()
                    ? "倒计时测试已完成 · 已自动保存"
                    : "倒计时测试已完成 · 已自动保存：" + savedTitle);
            statusText.setTextColor(secondaryTextColor());
        } else if (!samples.isEmpty()) {
            statusText.setText(currentRunSaved ? "测试已停止 · 已保存记录" : "测试已停止");
            statusText.setTextColor(secondaryTextColor());
        } else {
            statusText.setText("尚未开始测试");
            statusText.setTextColor(secondaryTextColor());
        }

        statsText.setText(buildStats(samples));
        curveView.setData(samples);
        histogramView.setData(samples);
    }

    private String formatCountdown(long autoStopAtElapsedRealtime) {
        long remainingMs = Math.max(0L, autoStopAtElapsedRealtime - SystemClock.elapsedRealtime());
        long sec = (remainingMs + 999L) / 1000L;
        return String.format(Locale.US, "%d:%02d", sec / 60L, sec % 60L);
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
        bg.setColor(cardColor());
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), borderColor());
        l.setBackground(bg);
        return l;
    }

    private void setHapticClick(View view, View.OnClickListener listener) {
        view.setOnClickListener(v -> {
            haptic(v);
            listener.onClick(v);
        });
    }

    private void haptic(View view) {
        if (hapticsEnabled && view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        }
    }

    private int backgroundColor() {
        return darkMode ? Color.rgb(18, 19, 22) : Color.rgb(246, 247, 249);
    }

    private int cardColor() {
        return darkMode ? Color.rgb(31, 33, 37) : Color.WHITE;
    }

    private int borderColor() {
        return darkMode ? Color.rgb(57, 60, 66) : Color.rgb(228, 229, 232);
    }

    private int primaryTextColor() {
        return darkMode ? Color.rgb(242, 243, 245) : Color.rgb(25, 25, 28);
    }

    private int secondaryTextColor() {
        return darkMode ? Color.rgb(185, 188, 195) : Color.DKGRAY;
    }

    private int accentColor() {
        return darkMode ? Color.rgb(120, 177, 255) : Color.rgb(20, 70, 145);
    }

    private String getCurrentVersionName() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null || version.trim().isEmpty() ? "0.0.0" : version;
        } catch (Exception ignored) {
            return "0.0.0";
        }
    }

    private void checkForUpdates(Button button) {
        button.setEnabled(false);
        button.setText("检查中…");
        String currentVersion = getCurrentVersionName();
        UpdateChecker.check(currentVersion, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateChecker.Result result) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("检查 GitHub 更新");
                    if (!result.hasUpdate) {
                        Toast.makeText(MainActivity.this,
                                "当前已是最新版本（v" + currentVersion + "）",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("发现新版本 v" + result.latestVersion)
                            .setMessage("将前往 GitHub Release 页面。你可以在 Assets 中点击 APK 下载，不在应用内执行更新。")
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("前往 GitHub 下载", (dialog, which) ->
                                    openReleasePage(result.releaseUrl))
                            .show();
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText("检查 GitHub 更新");
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("检查更新失败")
                            .setMessage("暂时无法连接 GitHub。核心测试功能不受影响，你也可以稍后再试。\n\n"
                                    + error.getClass().getSimpleName())
                            .setPositiveButton("知道了", null)
                            .show();
                });
            }
        });
    }

    private void openReleasePage(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (currentPage == PAGE_DETAIL) {
            showRecordsPage();
            return;
        }
        if (currentPage == PAGE_RECORDS || currentPage == PAGE_SETTINGS) {
            showHomePage();
            return;
        }
        super.onBackPressed();
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
