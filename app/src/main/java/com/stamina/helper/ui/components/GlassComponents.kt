package com.stamina.helper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================== 工具函数 ====================

@Composable
fun isDarkTheme(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    return (0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue) < 0.5f
}

// ==================== 玻璃卡片 ====================

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val dark = isDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val fill = if (dark) {
        Brush.verticalGradient(
            0f to Color(0xFF1C2420),
            0.5f to Color(0xFF161D1A),
            1f to Color(0xFF131917)
        )
    } else {
        Brush.verticalGradient(
            0f to Color(0xF5FFFFFF),
            0.5f to Color(0xE8FAFDFB),
            1f to Color(0xDDF1F7F2)
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(
                BorderStroke(1.dp, if (dark) Color(0x1AFFFFFF) else Color(0x80FFFFFF)),
                shape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x18FFFFFF),
                        0.25f to Color(0x00FFFFFF),
                        1f to Color(0x00FFFFFF)
                    )
                )
        )
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

// ==================== Miuix 风格选项 ====================

@Composable
fun MiuixChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chipScale"
    )

    val dark = isDarkTheme()
    val base = accent ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)

    val bg by animateColorAsState(
        when {
            !enabled -> if (dark) Color(0x12FFFFFF) else Color(0x60FFFFFF)
            selected -> base
            else -> if (dark) Color(0x1CFFFFFF) else Color(0x80FFFFFF)
        },
        label = "chipBg"
    )
    val fg by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            selected -> if (dark) Color(0xFF07120C) else Color.White
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        },
        label = "chipFg"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(bg)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) Color.Transparent
                    else if (dark) Color(0x18FFFFFF) else Color(0x66FFFFFF)
                ),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ==================== 玻璃主按钮 ====================

@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.97f else 1f,
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btnScale"
    )

    val dark = isDarkTheme()
    val base = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        0f to base,
                        0.55f to base.copy(alpha = 0.92f),
                        1f to base.copy(alpha = 0.80f)
                    )
                } else {
                    Brush.horizontalGradient(
                        0f to MaterialTheme.colorScheme.surfaceVariant,
                        1f to MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            )
            .border(
                BorderStroke(1.dp, if (enabled) Color(0x28FFFFFF) else Color(0x10FFFFFF)),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                if (dark) Color(0xFF07120C) else Color.White
            } else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 玻璃描边按钮 ====================

@Composable
fun GlassOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "outlineScale"
    )
    val dark = isDarkTheme()
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(shape)
            .background(if (dark) Color(0x0DFFFFFF) else Color(0x50FFFFFF))
            .border(
                BorderStroke(1.dp, if (dark) Color(0x1CFFFFFF) else Color(0x5CFFFFFF)),
                shape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}

// ==================== 分区标题 ====================

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==================== 状态徽章 ====================

@Composable
fun StatusBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}