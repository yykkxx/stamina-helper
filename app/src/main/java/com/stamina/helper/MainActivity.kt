package com.stamina.helper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.stamina.helper.prefs.AppPreferences
import com.stamina.helper.root.RootChecker
import com.stamina.helper.service.FloatingWindowService
import com.stamina.helper.service.StaminaAccessibilityService
import com.stamina.helper.ui.components.*
import com.stamina.helper.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val PRESET_TIMES = listOf(1f, 3f, 5f, 6f, 8f, 10f, 15f, 30f)
val PRESET_SWIPE_DURATIONS = listOf(100L, 200L, 300L, 400L, 500L)
val LOOP_GAPS = listOf(1000L, 2000L, 3000L, 5000L, 10000L)
val LOOP_COUNTS = listOf(0, 3, 5, 10, 20)

val AUTO_MODES = listOf(
    Triple(AppPreferences.MODE_AUTO_OFF, "关闭", "全程手动"),
    Triple(AppPreferences.MODE_AUTO_CANCEL, "仅点取消", "点一次取消"),
    Triple(AppPreferences.MODE_AUTO_LOOP, "循环", "持续重复")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}

@Composable
fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> onResult(granted) }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onResult(true)
    }
}

fun hasNotificationPermission(context: android.content.Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    else true

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()
    val isDark = isDarkTheme()

    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isAccessibility by remember { mutableStateOf(StaminaAccessibilityService.isServiceEnabled(context)) }
    var isRoot by remember { mutableStateOf(false) }
    var isServiceRunning by remember { mutableStateOf(false) }
    var hasNotif by remember { mutableStateOf(hasNotificationPermission(context)) }
    var autoGrantMsg by remember { mutableStateOf<String?>(null) }

    var waitSeconds by remember { mutableStateOf(prefs.waitSeconds) }
    var swipeDurationMs by remember { mutableStateOf(prefs.swipeDurationMs) }
    var execMode by remember { mutableStateOf(prefs.executionMode) }
    var customTimeText by remember { mutableStateOf("") }
    var autoMode by remember { mutableStateOf(prefs.autoActionMode) }
    var loopGapMs by remember { mutableStateOf(prefs.loopGapMs) }
    var maxLoopCount by remember { mutableStateOf(prefs.maxLoopCount) }
    var cancelDone by remember { mutableStateOf(prefs.cancelCalibrated) }
    var swipeDone by remember { mutableStateOf(prefs.swipeCalibrated) }

    val requestNotif = rememberNotificationPermissionRequest { hasNotif = it }

    val canRun = hasOverlay && (
        (execMode == "accessibility" && isAccessibility) ||
        (execMode == "root" && isRoot)
    )

    val allReady = canRun && (
        autoMode == AppPreferences.MODE_AUTO_OFF ||
        (autoMode == AppPreferences.MODE_AUTO_CANCEL && cancelDone) ||
        (autoMode == AppPreferences.MODE_AUTO_LOOP && cancelDone && swipeDone)
    )

    LaunchedEffect(isRoot, isAccessibility) {
        if (execMode == "root" && !isRoot && isAccessibility) {
            execMode = "accessibility"; prefs.executionMode = "accessibility"
        } else if (execMode == "accessibility" && !isAccessibility && isRoot) {
            execMode = "root"; prefs.executionMode = "root"
        }
    }

    LaunchedEffect(Unit) {
        isRoot = RootChecker.isRootAvailable()
        if (isRoot) {
            scope.launch {
                autoGrantMsg = "正在通过 Root 自动授权..."
                val r = withContext(Dispatchers.IO) { RootChecker.grantAllPermissions(context) }
                hasOverlay = Settings.canDrawOverlays(context)
                hasNotif = hasNotificationPermission(context)
                autoGrantMsg = when {
                    r.overlayGranted && r.notificationGranted -> "Root 自动授权完成 ✓"
                    r.overlayGranted -> "悬浮窗已授权，通知需手动开启"
                    else -> "Root 授权部分失败，请手动授权"
                }
            }
        }
    }

    LaunchedEffect(hasNotif) {
        if (!hasNotif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestNotif()
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
                isAccessibility = StaminaAccessibilityService.isServiceEnabled(context)
                hasNotif = hasNotificationPermission(context)
                cancelDone = prefs.cancelCalibrated
                swipeDone = prefs.swipeCalibrated
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // ===== 背景 =====
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    if (isDark) listOf(Color(0xFF0A0F0C), Color(0xFF101A15), Color(0xFF0A0F0C))
                    else listOf(Color(0xFFF3F8F5), Color(0xFFFBFDFC), Color(0xFFEDF5F0))
                )
            )
        )
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-70).dp, y = (-60).dp)
                .blur(90.dp)
                .background(
                    Color(0xFF34D399).copy(alpha = if (isDark) 0.14f else 0.10f),
                    RoundedCornerShape(50)
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(280.dp)
                .offset(x = 80.dp, y = 180.dp)
                .blur(90.dp)
                .background(
                    Color(0xFF60A5FA).copy(alpha = if (isDark) 0.12f else 0.09f),
                    RoundedCornerShape(50)
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 40.dp)
        ) {
            Spacer(Modifier.height(52.dp))

            // ===== 标题 =====
            Text(
                "体力小助手", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "自动滑动引出退出弹窗 · 计时回复体力",
                fontSize = 12.5.sp, maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(16.dp))

            // ===== 就绪状态条 =====
            ReadyBar(allReady, canRun, hasOverlay, isAccessibility, isRoot, cancelDone, swipeDone, autoMode)

            autoGrantMsg?.let { msg ->
                Spacer(Modifier.height(8.dp))
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp, contentPadding = PaddingValues(11.dp)
                ) {
                    Text(
                        msg, fontSize = 12.5.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (msg.contains("✓")) Color(0xFF2E9E5B) else Color(0xFFE08A2E)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ===== 权限状态 =====
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                SectionTitle("权限", modifier = Modifier.padding(bottom = 12.dp))
                PermRow("悬浮窗", hasOverlay, "授权") {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")))
                }
                ThinDivider()
                PermRow("通知", hasNotif, "授权") { requestNotif() }
                ThinDivider()
                PermRow("无障碍", isAccessibility, "开启") {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                ThinDivider()
                PermRow("Root", isRoot, "") {}
            }

            // ===== 定位器校准 =====
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                SectionTitle(
                    "定位器", subtitle = if (cancelDone && swipeDone) "已完成" else "待设置",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                CalibRow(
                    index = "1", title = "取消按钮", color = Color(0xFF22C55E),
                    done = cancelDone,
                    detail = if (cancelDone)
                        "X ${(prefs.cancelPointX * 100).toInt()}% · Y ${(prefs.cancelPointY * 100).toInt()}%"
                    else "对准退出弹窗的取消键",
                    onClick = { context.startActivity(Intent(context, CalibrationActivity::class.java)) }
                )
                Spacer(Modifier.height(8.dp))
                CalibRow(
                    index = "2", title = "前进键拖拽", color = Color(0xFF3B82F6),
                    done = swipeDone,
                    detail = if (swipeDone)
                        "起 ${(prefs.swipeStartX * 100).toInt()}%,${(prefs.swipeStartY * 100).toInt()}%" +
                            " → 终 ${(prefs.swipeEndX * 100).toInt()}%,${(prefs.swipeEndY * 100).toInt()}%"
                    else "设置起点与终点",
                    onClick = { context.startActivity(Intent(context, CalibrationActivity::class.java)) }
                )
            }

            // ===== 等待时间 =====
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                SectionTitle(
                    "等待时间", subtitle = "${String.format("%.2f", waitSeconds)}s",
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                PRESET_TIMES.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        row.forEach { t ->
                            MiuixChip(
                                text = formatTimeLabel(t),
                                selected = kotlin.math.abs(waitSeconds - t) < 0.001f,
                                onClick = {
                                    waitSeconds = t; prefs.waitSeconds = t; customTimeText = ""
                                },
                                modifier = Modifier.weight(1f).padding(end = 6.dp)
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f).padding(end = 6.dp)) }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    "自定义（秒）", fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customTimeText,
                        onValueChange = { s ->
                            if (s.count { it == '.' } <= 1)
                                customTimeText = s.filter { it.isDigit() || it == '.' }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("7.50", fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    MiuixChip(
                        text = "保存", selected = false,
                        onClick = {
                            val v = customTimeText.toFloatOrNull()
                            if (v != null && v > 0f) {
                                waitSeconds = v; prefs.waitSeconds = v; customTimeText = ""
                            }
                        },
                        modifier = Modifier.width(70.dp)
                    )
                }
            }

            // ===== 滑动时长 =====
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                SectionTitle(
                    "滑动时长", subtitle = "${swipeDurationMs}ms",
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    PRESET_SWIPE_DURATIONS.forEachIndexed { i, ms ->
                        MiuixChip(
                            text = "${ms}ms",
                            selected = swipeDurationMs == ms,
                            onClick = { swipeDurationMs = ms; prefs.swipeDurationMs = ms },
                            modifier = Modifier.weight(1f).let {
                                if (i < PRESET_SWIPE_DURATIONS.lastIndex) it.padding(end = 5.dp) else it
                            }
                        )
                    }
                }
            }

            // ===== 执行模式 =====
            GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                SectionTitle("执行模式", modifier = Modifier.padding(bottom = 12.dp))
                Row {
                    MiuixChip(
                        text = "Root", selected = execMode == "root",
                        onClick = { execMode = "root"; prefs.executionMode = "root" },
                        enabled = isRoot,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    MiuixChip(
                        text = "无障碍", selected = execMode == "accessibility",
                        onClick = { execMode = "accessibility"; prefs.executionMode = "accessibility" },
                        enabled = isAccessibility,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ===== 自动点击 =====
            AutoActionCard(
                autoMode = autoMode,
                onModeChange = { autoMode = it; prefs.autoActionMode = it },
                loopGapMs = loopGapMs,
                onGapChange = { loopGapMs = it; prefs.loopGapMs = it },
                maxLoopCount = maxLoopCount,
                onLoopCountChange = { maxLoopCount = it; prefs.maxLoopCount = it },
                cancelDone = cancelDone,
                swipeDone = swipeDone,
                waitSeconds = waitSeconds,
                onGotoCalibration = {
                    context.startActivity(Intent(context, CalibrationActivity::class.java))
                }
            )

            Spacer(Modifier.height(14.dp))

            // ===== 操作 =====
            GlassPrimaryButton(
                text = if (allReady) "启动悬浮窗" else if (!canRun) "请先完成授权" else "请先校准定位器",
                onClick = { FloatingWindowService.start(context); isServiceRunning = true },
                enabled = allReady,
                modifier = Modifier.fillMaxWidth()
            )

            if (isServiceRunning) {
                Spacer(Modifier.height(10.dp))
                GlassOutlineButton(
                    text = "停止服务",
                    onClick = { FloatingWindowService.stop(context); isServiceRunning = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))

            // ===== 使用说明 =====
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("使用说明", modifier = Modifier.padding(bottom = 10.dp))
                listOf(
                    "首次使用先完成两个定位器校准",
                    "校准时可切到游戏，浮层不遮挡操作",
                    "进入游戏后点悬浮窗「回复」开始",
                    "循环模式倒计时中可点「停止」中断"
                ).forEach {
                    Row(modifier = Modifier.padding(bottom = 7.dp)) {
                        Box(
                            modifier = Modifier.padding(top = 5.dp).size(4.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            it, fontSize = 12.sp, maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ==================== 就绪状态条 ====================

@Composable
private fun ReadyBar(
    allReady: Boolean, canRun: Boolean,
    hasOverlay: Boolean, isA11y: Boolean, isRoot: Boolean,
    cancelDone: Boolean, swipeDone: Boolean, autoMode: Int
) {
    val color = if (allReady) Color(0xFF22C55E) else Color(0xFFF59E0B)
    val text = when {
        allReady -> "就绪 · 点击下方启动"
        !canRun -> "需要权限授权"
        autoMode == AppPreferences.MODE_AUTO_CANCEL && !cancelDone -> "需校准取消按钮"
        autoMode == AppPreferences.MODE_AUTO_LOOP && (!cancelDone || !swipeDone) -> "需校准定位器"
        else -> "准备中..."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(8.dp)
                .background(color, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text, fontSize = 12.5.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = color, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== 自动点击卡片 ====================

@Composable
private fun AutoActionCard(
    autoMode: Int,
    onModeChange: (Int) -> Unit,
    loopGapMs: Long,
    onGapChange: (Long) -> Unit,
    maxLoopCount: Int,
    onLoopCountChange: (Int) -> Unit,
    cancelDone: Boolean,
    swipeDone: Boolean,
    waitSeconds: Float,
    onGotoCalibration: () -> Unit
) {
    val needCancel = autoMode != AppPreferences.MODE_AUTO_OFF
    val needSwipe = autoMode == AppPreferences.MODE_AUTO_LOOP
    val ready = (!needCancel || cancelDone) && (!needSwipe || swipeDone)

    GlassCard(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        SectionTitle(
            "自动点击",
            subtitle = when (autoMode) {
                AppPreferences.MODE_AUTO_CANCEL -> "结束点一次取消"
                AppPreferences.MODE_AUTO_LOOP -> if (maxLoopCount > 0) "循环 · ${maxLoopCount}轮" else "循环 · 无限"
                else -> "全程手动"
            },
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ---- 模式选择 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isDarkTheme()) Color(0x14FFFFFF) else Color(0x55FFFFFF))
                .padding(3.dp)
        ) {
            AUTO_MODES.forEach { (mode, label, _) ->
                val sel = autoMode == mode
                val avail = when (mode) {
                    AppPreferences.MODE_AUTO_CANCEL -> cancelDone
                    AppPreferences.MODE_AUTO_LOOP -> cancelDone && swipeDone
                    else -> true
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(enabled = avail) { onModeChange(mode) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 13.sp, maxLines = 1,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            !avail -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            sel -> if (isDarkTheme()) Color(0xFF07120C) else Color.White
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- 流程可视化 ----
        FlowDiagram(
            autoMode = autoMode,
            loopGapMs = loopGapMs,
            waitSeconds = waitSeconds,
            cancelDone = cancelDone,
            swipeDone = swipeDone
        )

        // ---- 循环设置（仅循环模式）----
        if (autoMode == AppPreferences.MODE_AUTO_LOOP) {
            Spacer(Modifier.height(14.dp))
            Text(
                "两轮之间等待", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkTheme()) Color(0x14FFFFFF) else Color(0x55FFFFFF))
                    .padding(3.dp)
            ) {
                LOOP_GAPS.forEach { g ->
                    val sel = loopGapMs == g
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (sel) MaterialTheme.colorScheme.secondary else Color.Transparent)
                            .clickable { onGapChange(g) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${g / 1000}s",
                            fontSize = 12.sp, maxLines = 1,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) {
                                if (isDarkTheme()) Color(0xFF06121F) else Color.White
                            } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "最大轮数", fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDarkTheme()) Color(0x14FFFFFF) else Color(0x55FFFFFF))
                    .padding(3.dp)
            ) {
                LOOP_COUNTS.forEach { c ->
                    val sel = maxLoopCount == c
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onLoopCountChange(c) }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (c == 0) "无限" else "$c",
                            fontSize = 12.sp, maxLines = 1,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (sel) {
                                if (isDarkTheme()) Color(0xFF07120C) else Color.White
                            } else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }

        // ---- 依赖提示 ----
        if (!ready) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFB45309).copy(alpha = 0.10f))
                    .padding(start = 11.dp, top = 9.dp, bottom = 9.dp, end = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚠", fontSize = 13.sp, color = Color(0xFFD97706))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        !cancelDone -> "需校准「取消按钮」"
                        else -> "需校准「前进键」"
                    },
                    fontSize = 11.5.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFB45309),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                MiuixChip(
                    text = "去校准", selected = false,
                    onClick = onGotoCalibration,
                    accent = Color(0xFFD97706),
                    modifier = Modifier.width(70.dp)
                )
            }
        }
    }
}

// ==================== 流程图 ====================

@Composable
private fun RowScope.FlowNode(
    text: String, sub: String, color: Color, ok: Boolean
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (ok) 0.14f else 0.06f))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text, fontSize = 11.5.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) color else color.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            sub, fontSize = 9.5.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = if (ok) 0.9f else 0.4f)
        )
    }
}

@Composable
private fun DownArrow(label: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (label != null) {
            Text(
                label, fontSize = 10.sp, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("↓", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun FlowDiagram(
    autoMode: Int,
    loopGapMs: Long,
    waitSeconds: Float,
    cancelDone: Boolean,
    swipeDone: Boolean
) {
    val green = Color(0xFF22C55E)
    val blue = Color(0xFF3B82F6)
    val amber = Color(0xFFF59E0B)
    val gray = Color(0xFF64748B)
    val wait = "${String.format("%.1f", waitSeconds)}s"

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (autoMode) {
                AppPreferences.MODE_AUTO_OFF -> {
                    FlowNode("滑动", "引出弹窗", blue, swipeDone)
                    Spacer(Modifier.width(6.dp))
                    FlowNode("倒计时", wait, amber, true)
                    Spacer(Modifier.width(6.dp))
                    FlowNode("手动", "自行点击", gray, true)
                }
                AppPreferences.MODE_AUTO_CANCEL -> {
                    FlowNode("滑动", "引出弹窗", blue, swipeDone)
                    Spacer(Modifier.width(6.dp))
                    FlowNode("倒计时", wait, amber, true)
                    Spacer(Modifier.width(6.dp))
                    FlowNode("点取消", "一次", green, cancelDone)
                }
                else -> {
                    FlowNode("滑动", "引出弹窗", blue, swipeDone)
                    Spacer(Modifier.width(6.dp))
                    FlowNode("倒计时", wait, amber, true)
                    Spacer(Modifier.width(6.dp))
                    FlowNode("点取消", "返回游戏", green, cancelDone)
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        when (autoMode) {
            AppPreferences.MODE_AUTO_OFF -> Text(
                "全程不自动操作，倒计时结束由你手动点击",
                fontSize = 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppPreferences.MODE_AUTO_CANCEL -> Text(
                "→ 自动点一次取消后结束",
                fontSize = 11.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> DownArrow("等待 ${loopGapMs / 1000f}s 后回到第一步，循环进行")
        }
    }
}

// ==================== 辅助组件 ====================

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    )
}

@Composable
private fun CalibRow(
    index: String,
    title: String,
    color: Color,
    done: Boolean,
    detail: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (done) 0.10f else 0.05f))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(color.copy(alpha = if (done) 1f else 0.35f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) "✓" else index,
                fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, fontSize = 13.5.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                detail, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            if (done) "重设" else "校准",
            fontSize = 11.5.sp, maxLines = 1,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PermRow(name: String, granted: Boolean, buttonText: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name, fontSize = 13.5.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (granted) "已授权" else "未授权",
            fontSize = 12.sp, maxLines = 1,
            color = if (granted) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!granted && buttonText.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            MiuixChip(
                text = buttonText, selected = false, onClick = onClick,
                modifier = Modifier.width(66.dp)
            )
        }
    }
}

fun formatTimeLabel(t: Float): String =
    if (t == t.toInt().toFloat()) "${t.toInt()}s" else "${t}s"