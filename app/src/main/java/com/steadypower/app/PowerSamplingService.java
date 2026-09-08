package com.steadypower.app;

import android.app.BatteryManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PowerSamplingService extends Service {
    public static final String ACTION_START = "com.steadypower.app.START";
    private static final String CHANNEL_ID = "steadypower_sampling";
    private static final int NOTIFICATION_ID = 1001;

    private ScheduledExecutorService scheduler;
    private BatteryManager batteryManager;

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

        startForeground(NOTIFICATION_ID, buildNotification());

        if (scheduler != null && !scheduler.isShutdown()) {
            return START_NOT_STICKY;
        }

        PowerRepository.start(SystemClock.elapsedRealtime());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::sampleOnce, 0, 1, TimeUnit.SECONDS);
        return START_NOT_STICKY;
    }

    private void sampleOnce() {
        try {
            long currentUa = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) {
                PowerRepository.setLastError("无法读取电池状态");
                return;
            }

            int voltageMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;

            if (currentUa == Long.MIN_VALUE || voltageMv <= 0) {
                PowerRepository.setLastError("设备未提供可用的瞬时电流或电压数据");
                return;
            }

            double currentA = Math.abs(currentUa) / 1_000_000.0;
            double voltageV = voltageMv / 1000.0;
            double powerW = currentA * voltageV;
            if (!Double.isFinite(powerW) || currentA > 20.0 || voltageV > 6.0 || powerW > 100.0) {
                PowerRepository.setLastError("检测到明显无效的传感器读数");
                return;
            }

            long nowElapsed = SystemClock.elapsedRealtime();
            long elapsed = Math.max(0L, nowElapsed - PowerRepository.getStartElapsedRealtime());
            PowerRepository.add(new PowerRepository.Sample(
                    System.currentTimeMillis(), elapsed, currentUa, voltageMv, powerW, charging));
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
        return builder
                .setSmallIcon(com.steadypower.app.R.drawable.ic_stat_power)
                .setContentTitle("SteadyPower 正在测试")
                .setContentText("每秒记录一次电池端功耗")
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
