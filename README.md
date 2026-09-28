# 体力小助手 Stamina Helper

> Android 游戏体力自动回复助手 · 自动滑出退出弹窗 · 倒计时 · 循环执行

## 功能简介

在游戏（如原神）中自动执行滑动操作呼出退出弹窗，倒计时后自动点击取消按钮回到游戏，实现体力回复的自动化循环。

### 核心功能

- **自动滑动**：从屏幕边缘滑出游戏退出弹窗，可配置滑动时长
- **倒计时回复**：设置等待时间（秒），倒计时结束后自动操作
- **两种执行模式**：Root 模式（`input` 命令） / 无障碍模式（AccessibilityService）
- **自动动作模式**：
  - 关闭：全程手动
  - 仅点取消：滑动→倒计时→点击取消，结束
  - 循环：滑动→倒计时→点取消→等待→再滑动…（可设置轮数上限与间隔）
- **定位器校准**：在游戏中通过可拖动悬浮窗精确定位「取消按钮」和「前进键拖拽路径」，坐标归一化存储，换设备仍有效
- **悬浮窗控制**：桌面悬浮窗实时显示状态（就绪/倒计时/循环），支持开始/停止

### 技术特点

- **连点器式穿透架构**：定位器浮层使用 `FLAG_NOT_TOUCHABLE`，触摸事件 100% 穿透到游戏
- **极简悬浮窗**：贴边可拖动控制面板 + 圆形定位按钮，不遮挡游戏画面
- **白色主题**：简洁的灰白配色，Material 3 风格
- **Compose UI**：全 Jetpack Compose 构建，Glassmorphism 玻璃拟态组件

## 项目结构

```
app/src/main/java/com/stamina/helper/
├── MainActivity.kt              # 主界面
├── CalibrationActivity.kt        # 定位器校准页面
├── calibration/
│   └── CalibrationService.kt     # 校准悬浮窗服务
├── floating/
│   ├── FloatingWindowManager.kt  # 悬浮窗视图管理
│   └── FloatingState.kt         # 悬浮窗状态定义
├── service/
│   ├── FloatingWindowService.kt  # 悬浮窗前台服务
│   └── StaminaAccessibilityService.kt  # 无障碍服务
├── gesture/
│   ├── GestureExecutor.kt        # 手势执行接口
│   ├── RootGestureExecutor.kt    # Root 模式实现
│   ├── AccessibilityGestureExecutor.kt  # 无障碍模式实现
│   └── GestureConfig.kt          # 手势配置
├── timer/
│   └── StaminaTimer.kt          # 倒计时器
├── root/
│   └── RootChecker.kt            # Root 权限检测
├── prefs/
│   └── AppPreferences.kt         # 偏好设置管理
└── ui/
    ├── theme/                     # Material 3 主题
    └── components/                # 玻璃拟态组件
```

## 构建

```bash
# 环境要求
# - Android SDK 35
# - JDK 17
# - Gradle 9.x

./gradlew assembleDebug
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗（校准浮层、状态悬浮窗） |
| `FOREGROUND_SERVICE` | 悬浮窗前台服务保活 |
| `POST_NOTIFICATIONS` | 前台服务通知 |
| 无障碍服务 | 无障碍模式下执行手势 |

## 配置项

| 配置 | 默认值 | 范围 |
|------|--------|------|
| 等待时间 | 5 秒 | - |
| 滑动时长 | 200ms | 100~500ms |
| 执行模式 | root | root / accessibility |
| 自动动作 | 关闭 | 关闭 / 仅取消 / 循环 |
| 循环间隔 | 2000ms | 500~10000ms |
| 循环轮数 | 0（无限） | 0~999 |

## License

MIT
