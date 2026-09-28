package com.stamina.helper

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stamina.helper.calibration.CalibMode
import com.stamina.helper.calibration.CalibrationService
import com.stamina.helper.prefs.AppPreferences
import com.stamina.helper.ui.components.*
import com.stamina.helper.ui.theme.MyApplicationTheme

/**
 * 定位器校准页面
 *
 * 流程：点击「开始校准」→ 浮层出现在游戏之上 →
 *       拖动定位点对准目标 → 点「保存」
 */
class CalibrationActivity : ComponentActivity() {

    private var calibService: CalibrationService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CalibrationScreen(
                        onBack = { finish() },
                        onLaunch = { mode ->
                            if (calibService == null) {
                                calibService = CalibrationService(this, mode) { saved ->
                                    calibService = null
                                    // 校准结束后回到前台
                                    val intent = Intent(this, CalibrationActivity::class.java)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    startActivity(intent)
                                }.also { it.show() }

                                // Activity 退到后台，用户直接回到游戏
                                moveTaskToBack(true)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        calibService?.dismiss()
        calibService = null
        super.onDestroy()
    }
}

@Composable
fun CalibrationScreen(
    onBack: () -> Unit,
    onLaunch: (CalibMode) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val isDark = isDarkTheme()

    var cancelDone by remember { mutableStateOf(prefs.cancelCalibrated) }
    var swipeDone by remember { mutableStateOf(prefs.swipeCalibrated) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            cancelDone = prefs.cancelCalibrated
            swipeDone = prefs.swipeCalibrated
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    BackHandler { onBack() }

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
            modifier = Modifier.size(300.dp)
                .offset(x = (-60).dp, y = (-40).dp)
                .blur(90.dp)
                .background(
                    Color(0xFF34D399).copy(alpha = if (isDark) 0.14f else 0.10f),
                    RoundedCornerShape(50)
                )
        )
        Box(
            modifier = Modifier.align(Alignment.TopEnd)
                .size(260.dp)
                .offset(x = 70.dp, y = 220.dp)
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

            MiuixChip(
                text = "‹ 返回", selected = false, onClick = onBack,
                modifier = Modifier.width(88.dp)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                "定位器校准", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "在游戏中对准目标位置，只需一次",
                fontSize = 12.5.sp, maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            // ===== 定位器 1 =====
            CalibCard(
                index = "1",
                title = "取消按钮",
                desc = "退出弹窗上的「取消」键",
                color = Color(0xFF22C55E),
                done = cancelDone,
                detail = if (cancelDone)
                    "X ${(prefs.cancelPointX * 100).toInt()}%　Y ${(prefs.cancelPointY * 100).toInt()}%"
                else "尚未校准",
                onCalibrate = { onLaunch(CalibMode.CANCEL) }
            )

            Spacer(Modifier.height(10.dp))

            // ===== 定位器 2 =====
            CalibCard(
                index = "2",
                title = "前进键拖拽",
                desc = "蓝色起点 + 绿色终点",
                color = Color(0xFF3B82F6),
                done = swipeDone,
                detail = if (swipeDone)
                    "起 ${(prefs.swipeStartX * 100).toInt()}%,${(prefs.swipeStartY * 100).toInt()}%" +
                        "　终 ${(prefs.swipeEndX * 100).toInt()}%,${(prefs.swipeEndY * 100).toInt()}%"
                else "尚未校准",
                onCalibrate = { onLaunch(CalibMode.SWIPE) }
            )

            Spacer(Modifier.height(10.dp))

            // ===== 说明 =====
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                SectionTitle("怎么用", modifier = Modifier.padding(bottom = 10.dp))
                StepRow("1", "点「开始校准」，浮层出现并回到桌面")
                StepRow("2", "打开原神，引出退出弹窗")
                StepRow("3", "点面板「拖动」→ 圆形按钮变可拖动")
                StepRow("4", "拖到目标位置 → 点「完成」→ 点「确定位置」")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF22C55E).copy(alpha = 0.08f))
                        .padding(11.dp)
                ) {
                    Text(
                        "滑动定位有 1 和 2 两个按钮：1 拖到起点，2 拖到终点，中间有指示线。面板可随意拖动。",
                        fontSize = 11.5.sp, maxLines = 3,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (cancelDone || swipeDone) {
                Spacer(Modifier.height(14.dp))
                GlassOutlineButton(
                    text = "清除校准数据",
                    onClick = {
                        prefs.clearCalibration()
                        cancelDone = false
                        swipeDone = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** 校准卡片 */
@Composable
private fun CalibCard(
    index: String,
    title: String,
    desc: String,
    color: Color,
    done: Boolean,
    detail: String,
    onCalibrate: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color.copy(alpha = if (done) 1f else 0.32f),
                        RoundedCornerShape(11.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (done) "✓" else index,
                    fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, fontSize = 15.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (done) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "已校准", fontSize = 9.5.sp,
                                color = color, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    detail, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 预览示意
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.05f))
        ) {
            if (index == "1") {
                // 单点示意
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(26.dp)
                        .background(color, RoundedCornerShape(13.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(13.dp))
                )
            } else {
                // 双点 + 连线示意
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xFF3B82F6), RoundedCornerShape(10.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF3B82F6), Color(0xFF22C55E))
                                )
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(0xFF22C55E), RoundedCornerShape(10.dp))
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        MiuixChip(
            text = if (done) "重新校准" else "开始校准",
            selected = false,
            onClick = onCalibrate,
            accent = color,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(6.dp))
        Text(
            desc, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

/** 步骤行 */
@Composable
private fun StepRow(num: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(6.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                num, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text, fontSize = 12.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}