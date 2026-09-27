package com.steadypower.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PowerSamplingService extends Service {
    public static final String ACTION_START = "com.steadypower.app.START";
    public static final String EXTRA_AUTO_STOP_MS = "auto_stop_ms";
    public static final String EXTRA_AUTO_SAVE_NAME = "auto_save_name";

    private static final String CHANNEL_ID = "steadypower_sampling";
    private static final int NOTIFICATION_ID = 1001;

    private ScheduledExecutorService scheduler;
    private BatteryManager batteryManager;
    private long timedTestDurationMs;

    @Override
    public void onCreate() {
        super.onCreate();
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            return START_NOT_STICKY;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            return START_NOT_STICKY;
        }

        timedTestDurationMs = Math.max(0L, intent.getLongExtra(EXTRA_AUTO_STOP_MS, 0L));
        String autoSaveName = intent.getStringExtra(EXTRA_AUTO_SAVE_NAME);
        if (autoSaveName == null || autoSaveName.trim().isEmpty()) {
            autoSaveName = "倒计时测试";
        }
        final String recordName = autoSaveName.trim();

        startForeground(NOTIFICATION_ID, buildNotification());

        long startElapsed = SystemClock.elapsedRealtime();
        PowerRepository.start(startElapsed, timedTestDurationMs);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::sampleOnce, 0, 1, TimeUnit.SECONDS);

        if (timedTestDurationMs > 0L) {
            scheduler.schedule(() -> finishTimedTest(recordName),
                    timedTestDurationMs, TimeUnit.MILLISECONDS);
        }
        return START_NOT_STICKY;
    }

    private void finishTimedTest(String recordName) {
        try {
            List<PowerRepository.Sample> samples = PowerRepository.snapshot();
            if (samples.isEmpty()) {
                PowerRepository.setLastError("倒计时结束，但没有有效采样数据可保存");
            } else {
                RecordStore.Record record = RecordStore.save(this, recordName, samples);
                PowerRepository.markAutoSaved(record.id, record.displayTitle());
                PowerRepository.setLastError("");
            }
        } catch (Exception e) {
            PowerRepository.setLastError("倒计时结束，自动保存失败：" + e.getClass().getSimpleName());
        } finally {
            PowerRepository.stop();
            stopSelf();
        }
    }

    private void sampleOnce() {
        try {
            long rawCurrent = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                PowerRepository.setLastError("无法读取电池状态");
                return;
            }

            int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            if (rawCurrent == Long.MIN_VALUE || voltageMv <= 0) {
                PowerRepository.setLastError("设备未提供可用的瞬时电流或电压数据");
                return;
            }

            double voltageV = voltageMv / 1000.0;

            // Android API specifies microamps, but a few OEM/ROM combinations return milliamps.
            // If the nominal microamp interpretation gives an implausibly tiny screen-on power
            // while the milliamp interpretation remains within a normal phone discharge range,
            // normalize the value to microamps before calculating power.
            long normalizedCurrentUa = rawCurrent;
            double powerIfUa = (Math.abs(rawCurrent) / 1_000_000.0) * voltageV;
            double powerIfMa = (Math.abs(rawCurrent) / 1_000.0) * voltageV;
            if (rawCurrent != 0
                    && Math.abs(rawCurrent) < 10_000
                    && powerIfUa < 0.05
                    && powerIfMa >= 0.05
                    && powerIfMa <= 15.0) {
                normalizedCurrentUa = rawCurrent * 1000L;
            }

            double currentA = Math.abs(normalizedCurrentUa) / 1_000_000.0;
            double powerW = currentA * voltageV;
            if (!Double.isFinite(powerW) || currentA > 20.0 || voltageV > 6.0 || powerW > 100.0) {
                PowerRepository.setLastError("检测到明显无效的传感器读数");
                return;
            }

            long nowElapsed = SystemClock.elapsedRealtime();
            long elapsed = Math.max(0L, nowElapsed - PowerRepository.getStartElapsedRealtime());
            PowerRepository.add(new PowerRepository.Sample(
                    System.currentTimeMillis(), elapsed, normalizedCurrentUa, voltageMv, powerW, charging));
            PowerRepository.setLastError("");
        } catch (Throwable t) {
            PowerRepository.setLastError("采样失败：" + t.getClass().getSimpleName());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "功耗测试",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("SteadyPower 后台 1 Hz 采样");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        String content = timedTestDurationMs > 0L
                ? "倒计时测试进行中，结束后将自动保存"
                : "每秒记录一次电池端功耗";
        return builder
                .setSmallIcon(com.steadypower.app.R.drawable.ic_stat_power)
                .setContentTitle("SteadyPower 正在测试")
                .setContentText(content)
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    @Override
    public void onDestroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        PowerRepository.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
