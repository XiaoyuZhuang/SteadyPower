# ⚡ SteadyPower

**A lightweight Android power-consumption recorder for comparing steady-use power under different device strategies.**  
**一个用于比较不同设备策略下稳定使用功耗的轻量 Android 工具。**

[中文](#中文) · [English](#english)

---

# 中文

## 简介

SteadyPower 是一个简单的 Android 电池端功耗记录工具，适合做这类测试：

- 同一个 App 在不同 CPU / 调度 / 频率策略下的功耗对比
- 小说阅读、短视频、网页浏览等单一使用场景的功耗记录
- 观察瞬时功耗曲线和功耗分布
- 多次测试后保存记录并直接比较

它的目标不是做“电池管家”，而是尽量少做自动判断，把原始数据和统计结果直接展示出来。

## 当前功能

- 1 Hz 采样
- 读取电池电流与电压，计算电池端功率
- 实时原始功耗曲线
- 0.05 W 固定分箱的功耗直方图
- 典型功耗：全部有效样本的中位数
- 实际平均功耗：全部有效样本的算术平均
- 最低 / 最高功耗、测试时长、采样数
- CSV 导出
- 测试记录保存
- 自定义记录名称，不限制命名格式
- 保存记录时自动把中位功耗附加到标题，例如：`小说测试 · 1.627 W`
- 底部“主页 / 测试记录”双选项卡
- 历史记录可点击进入详情页，查看当次完整统计、原始曲线和直方图
- 历史记录同时保存逐秒采样数据
- 简单几何闪电图标

## ⚠️ 重要：后台运行权限

为了避免测试过程中出现一段时间没有采样，请尽量把 **SteadyPower 的相关权限全部允许**，并确保系统不会在后台杀掉它。

尤其建议：

1. 允许 SteadyPower 的通知权限和前台服务相关权限。
2. 在系统的电池 / 后台管理中，将 SteadyPower 设置为“允许后台运行”“无限制”或类似选项。
3. 如果系统支持“应用上锁 / 锁定后台 / 最近任务锁定”，建议把 SteadyPower 锁住。
4. 不要在测试过程中手动清理 SteadyPower 的后台任务。
5. 某些国产 ROM 的省电策略较激进，即使已经启动前台服务，也可能限制后台定时任务；如果曲线中出现明显缺段，请优先检查这些设置。

**如果后台权限没有放开，可能出现一段时间统计不上、采样间隔异常或测试被系统中断。**

## 推荐测试方法

为了让不同测试之间更容易比较，建议一次测试只做一件事：

1. 打开 SteadyPower，点击“开始测试”。
2. 立即切到需要测试的目标 App。
3. 保持同一种操作，例如一直阅读小说，或一直观看短视频。
4. 测试 2–5 分钟或更久。
5. 回到 SteadyPower，点击“停止”。
6. 查看中位数、平均值、原始曲线和直方图。
7. 保存记录并按自己的习惯命名。

## 如何理解两个主要功耗数字

### 典型功耗（中位数）

中位数对偶发的高功耗尖峰不敏感，更适合观察设备在大部分时间里处于什么功耗水平。

例如大部分时间在 1.5–1.7 W，偶尔出现 10 W 以上尖峰，中位数仍会比较接近长期常驻功耗。

### 实际平均功耗

算术平均会保留所有真实尖峰，因此更接近这段测试期间真实消耗的平均功率。

因此：

- 比较“常驻 / 典型运行功耗”时，优先参考中位数和直方图。
- 比较“实际续航影响”时，也应同时关注算术平均值。

## 直方图

SteadyPower 默认每 **0.05 W** 为一个区间统计出现次数。

最高的柱子代表设备在测试期间最常停留的功耗范围。对于一次只做单一任务的测试，这通常非常直观。

## 测量说明与局限

SteadyPower 使用 Android 提供的电池电流 / 电压数据估算：

`Power ≈ |Battery Current| × Battery Voltage`

因此它测到的是**电池端估算功耗**，不是外接专业功率计的实验室级测量值。

需要注意：

- 不同厂商对电流传感器的实现和刷新频率可能不同。
- 部分 ROM 对电流单位存在非标准实现；SteadyPower 已针对已知的 mA / μA 量级差异做兼容。
- App 本身会产生很小的采样开销，因此 A/B 对比时应保持相同测试流程。
- 正在充电时，电池净电流不能直接代表整机实际功耗，不建议在充电状态下进行对比测试。
- 屏幕亮度、刷新率、网络状态、温度、后台同步等都会影响结果；严格比较时应尽量保持一致。

## 隐私

SteadyPower 不需要联网权限。测试记录和历史采样数据保存在设备本地；CSV 只有在用户主动导出时才会写入所选位置。

## 构建

项目可直接使用 Gradle 构建：

```bash
gradle :app:assembleDebug
```

仓库中也提供 GitHub Actions 自动构建流程。

## 安装与更新

从 v0.3.0 起，项目使用固定签名进行构建。后续由本仓库构建并使用同一签名的版本可以直接覆盖更新，无需每次先卸载旧版本。

> 注意：不同来源、不同签名的 APK 仍然无法直接覆盖安装，这是 Android 的安全机制。

## 反馈

如果你遇到以下情况，欢迎提交 Issue：

- 功耗量级明显异常
- 曲线长时间没有采样
- 某些 ROM 无法后台记录
- 历史记录无法打开或数据异常
- 希望增加简单且不影响测量开销的功能

---

# English

## Overview

SteadyPower is a lightweight Android battery-side power recorder designed for simple, repeatable comparisons such as:

- Comparing the same app under different CPU, scheduler, or frequency strategies
- Recording power while reading, watching short videos, browsing, or performing another single-use workload
- Inspecting raw power curves and power distributions
- Saving multiple test runs for quick comparison later

The goal is not to be a battery-management suite. SteadyPower intentionally keeps the analysis simple and exposes the raw data instead of aggressively filtering or classifying it.

## Features

- 1 Hz sampling
- Battery current and voltage measurement with battery-side power estimation
- Raw power curve
- Power histogram with fixed 0.05 W bins
- Typical power: median of all valid samples
- Actual average power: arithmetic mean of all valid samples
- Min / max power, duration, and sample count
- CSV export
- Persistent test history
- Free-form test names with no required naming format
- Median power automatically appended to the saved title, e.g. `Reading test · 1.627 W`
- Bottom navigation with Home / Test History tabs
- Tap a history entry to open full statistics, raw curve, and histogram
- Per-second sample data is stored with each saved test
- Simple geometric lightning-bolt app icon

## ⚠️ Important: background-running permissions

To avoid missing chunks of data during a test, please **grant SteadyPower all relevant permissions** and make sure your Android system does not kill or throttle it in the background.

Recommended settings:

1. Allow notification and foreground-service related permissions for SteadyPower.
2. In Battery / Background settings, set SteadyPower to “Allow background activity”, “Unrestricted”, or the closest equivalent on your device.
3. If your ROM supports app locking / locking in Recents / keep-alive protection, lock SteadyPower.
4. Do not manually clear SteadyPower from Recents while a test is running.
5. Some OEM Android skins use aggressive battery-management policies. If your graph contains missing sections or unusually long sampling gaps, check those settings first.

**Without the required background permissions, some intervals may not be recorded, sampling may become irregular, or Android may stop the measurement service entirely.**

## Recommended workflow

For repeatable comparisons, use one workload per test:

1. Open SteadyPower and tap Start.
2. Immediately switch to the app you want to measure.
3. Keep doing the same type of activity, such as reading or watching short videos.
4. Run the test for 2–5 minutes or longer.
5. Return to SteadyPower and tap Stop.
6. Review the median, mean, raw curve, and histogram.
7. Save the run with any name you prefer.

## Understanding the two main power values

### Typical Power (Median)

The median is resistant to occasional high-power spikes. It is useful for estimating the power level where the device spends most of its time.

For example, if the device stays around 1.5–1.7 W most of the time but occasionally spikes above 10 W, the median remains close to the long-running baseline.

### Actual Average Power

The arithmetic mean keeps all real spikes, so it better represents the average power consumed over the whole test interval.

In practice:

- For typical / steady-use comparisons, focus on the median and histogram.
- For battery-life impact, also pay attention to the arithmetic mean.

## Histogram

SteadyPower uses fixed **0.05 W** bins by default.

The tallest bar indicates the power range where the device spent the most samples. For single-workload tests, this usually makes the dominant operating range easy to see.

## Measurement notes and limitations

SteadyPower estimates power from Android battery telemetry:

`Power ≈ |Battery Current| × Battery Voltage`

This is therefore a **battery-side estimate**, not a laboratory-grade external power-meter measurement.

Please keep in mind:

- Sensor quality and update frequency vary between manufacturers.
- Some ROMs expose non-standard current units; SteadyPower includes compatibility handling for known mA / μA scale differences.
- The app itself introduces a very small sampling overhead, so A/B tests should use the same procedure.
- While charging, battery net current does not directly represent total device power consumption, so charging-state comparisons are not recommended.
- Screen brightness, refresh rate, network conditions, temperature, and background sync can all affect results. Keep them consistent for controlled comparisons.

## Privacy

SteadyPower does not request Internet access. Test history and per-sample data are stored locally on the device. CSV files are only written when the user explicitly exports them.

## Build

Build directly with Gradle:

```bash
gradle :app:assembleDebug
```

The repository also includes a GitHub Actions workflow for automatic APK builds.

## Installation and updates

Starting with v0.3.0, builds use a persistent signing key. Future APKs built from this repository with the same key can be installed as normal in-place updates.

> APKs signed with a different key still cannot update an installed copy. This is an Android security requirement.

## Feedback

Issues are welcome, especially for:

- Obviously incorrect power scale
- Missing sections in the recorded curve
- Background-recording problems on specific ROMs
- History entries or saved data failing to open
- Small feature requests that do not add significant measurement overhead

---

Made for practical Android power comparisons, with intentionally simple statistics and transparent raw data.
