# SteadyPower v0.3.0

首个公开分享版本 / First public release

## 中文

SteadyPower 是一个轻量 Android 电池端功耗记录工具，用于比较同一使用场景下不同 CPU、调度、频率或系统策略的功耗表现。

### v0.3.0 主要功能

- 1 Hz 电池端功耗采样
- 原始功耗曲线
- 0.05 W 固定分箱功耗直方图
- 典型功耗（中位数）与实际平均功耗
- 最低 / 最高功耗、测试时长和采样数
- CSV 导出
- 自定义测试记录名称
- 保存完整逐秒测试数据
- “主页 / 测试记录”双选项卡
- 点击历史记录查看完整统计、曲线和直方图
- 简单几何闪电图标
- 针对部分 ROM 的 mA / μA 电流量级差异做兼容处理

### ⚠️ 后台运行权限

为了避免测试过程中出现缺失采样，请尽量允许 SteadyPower 的相关权限，并在系统电池 / 后台管理中设置为“允许后台运行”“无限制”或类似选项。如果系统支持最近任务上锁，也建议将 SteadyPower 锁定。

如果后台权限不足，可能出现某一段统计不上、采样间隔异常或系统停止测量服务。

### 测量说明

SteadyPower 根据 Android 提供的电池电流和电压数据估算功耗：

`Power ≈ |Battery Current| × Battery Voltage`

因此它适合做同一台设备上的 A/B 对比，但不是外接专业功率计的替代品。

---

## English

SteadyPower is a lightweight Android battery-side power recorder for comparing the same workload under different CPU, scheduler, frequency, or system strategies.

### Highlights in v0.3.0

- 1 Hz battery-side power sampling
- Raw power curve
- 0.05 W fixed-bin histogram
- Typical power (median) and actual average power
- Min / max power, duration, and sample count
- CSV export
- Free-form test names
- Full per-second sample history
- Home / Test History tabs
- Tap a saved test to inspect full statistics, curve, and histogram
- Simple geometric lightning-bolt icon
- Compatibility handling for some ROMs exposing non-standard mA / μA current scales

### ⚠️ Background-running permissions

To avoid missing samples, grant SteadyPower the relevant permissions and set it to “Allow background activity”, “Unrestricted”, or the closest equivalent in your system battery settings. If your ROM supports locking apps in Recents, locking SteadyPower is also recommended.

Without sufficient background permissions, parts of a test may be missing, sampling intervals may become irregular, or Android may stop the measurement service.

### Measurement note

SteadyPower estimates battery-side power from Android battery telemetry:

`Power ≈ |Battery Current| × Battery Voltage`

It is useful for repeatable A/B comparisons on the same device, but it is not a replacement for an external laboratory power meter.
