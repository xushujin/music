package com.azhan.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 拟态（Neumorphism）设计系统。
 *
 * 核心约束：Android 硬件加速画布**不支持** setShadowLayer / BlurMaskFilter（只对文字生效），
 * 所以这里不用模糊滤镜，改用「多层半透明圆角矩形叠加」来模拟柔和阴影 —— 纯 GPU 安全的绘制。
 *
 * 用法：
 *   凸起 = Modifier.neuRaised(shape).background(背景色, shape)
 *   凹陷 = Modifier.background(背景色, shape).neuInset(shape)
 * 两者的修饰符顺序不能颠倒，否则描边会被背景盖住。
 */
object Neu {

    val Background = Color(0xFFE7EAF0)
    val Highlight = Color(0xFFFFFFFF)
    val Shadow = Color(0xFFBFC6D4)
    val TextPrimary = Color(0xFF3B4351)
    val TextSecondary = Color(0xFF8D97A8)
    val Accent = Color(0xFF5B6CFF)
    val AccentSoft = Color(0xFF97A2FF)
}

object NeuShapes {
    val card = RoundedCornerShape(20.dp)
    val chip = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(50)
    val art = RoundedCornerShape(32.dp)
}

/**
 * 凸起效果：在元素背后画两圈柔和光晕（右下暗影 + 左上高光）。
 * 必须在它之后再 .background(...)，否则背景会盖住光晕。
 */
fun Modifier.neuRaised(
    shape: RoundedCornerShape = NeuShapes.card,
    blur: Dp = 12.dp,
    offset: Dp = 5.dp,
    highlight: Color = Neu.Highlight,
    shadow: Color = Neu.Shadow,
    layerAlpha: Float = 0.05f,
    highlightAlpha: Float = 0.085f,
): Modifier = drawBehind {
    val radius = shape.topStart.toPx(size, this)
    val blurPx = blur.toPx()
    val offsetPx = offset.toPx()
    // 先画暗影（右下），再画高光（左上）
    drawHalo(radius, shadow, blurPx, offsetPx, offsetPx, layerAlpha)
    drawHalo(radius, highlight, blurPx, -offsetPx, -offsetPx, highlightAlpha)
}

/**
 * 凹陷效果：在元素内部沿边缘画一圈方向性渐变描边。
 * 必须在 .background(...) 之后调用。
 */
fun Modifier.neuInset(
    shape: RoundedCornerShape = NeuShapes.card,
    stroke: Dp = 3.dp,
    highlight: Color = Neu.Highlight,
    shadow: Color = Neu.Shadow,
    shadowAlpha: Float = 0.5f,
    highlightAlpha: Float = 0.95f,
): Modifier = drawWithContent {
    drawContent()
    val radius = shape.topStart.toPx(size, this)
    val width = stroke.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to shadow.copy(alpha = shadowAlpha),
                0.45f to Color.Transparent,
                1f to highlight.copy(alpha = highlightAlpha),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        topLeft = Offset(width / 2f, width / 2f),
        size = Size((size.width - width).coerceAtLeast(0f), (size.height - width).coerceAtLeast(0f)),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = width),
    )
}

/** 用多层递进的圆角矩形叠出柔和光晕。从最大的一层开始画，越往里叠加越实。 */
private fun DrawScope.drawHalo(
    cornerRadius: Float,
    color: Color,
    blur: Float,
    dx: Float,
    dy: Float,
    layerAlpha: Float,
    steps: Int = 10,
) {
    if (blur <= 0f || layerAlpha <= 0f) return
    for (step in steps downTo 1) {
        val t = step.toFloat() / steps
        val grow = blur * t
        drawRoundRect(
            color = color.copy(alpha = layerAlpha),
            topLeft = Offset(-grow + dx * t, -grow + dy * t),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(cornerRadius + grow, cornerRadius + grow),
        )
    }
}

// ---------------------------------------------------------------------------
// 组件
// ---------------------------------------------------------------------------

/** 通用拟态容器。raised=true 凸起，false 凹陷。 */
@Composable
fun NeuBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = NeuShapes.card,
    raised: Boolean = true,
    color: Color = Neu.Background,
    blur: Dp = 12.dp,
    offset: Dp = 5.dp,
    highlight: Color = Neu.Highlight,
    shadow: Color = Neu.Shadow,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val decorated = if (raised) {
        Modifier
            .neuRaised(shape, blur, offset, highlight, shadow)
            .background(color, shape)
    } else {
        Modifier
            .background(color, shape)
            .neuInset(shape, highlight = highlight, shadow = shadow)
    }
    Box(modifier.then(decorated), content = content)
}

/** 圆形拟态图标按钮。按下时切换成凹陷态。 */
@Composable
fun NeuIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 46.dp,
    iconSize: Dp = 20.dp,
    tint: Color = Neu.TextPrimary,
    enabled: Boolean = true,
    color: Color = Neu.Background,
    highlight: Color = Neu.Highlight,
    shadow: Color = Neu.Shadow,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = NeuShapes.pill
    val decorated = if (pressed && enabled) {
        Modifier.background(color, shape).neuInset(shape, stroke = 2.5.dp, highlight = highlight, shadow = shadow)
    } else {
        Modifier.neuRaised(shape, blur = 9.dp, offset = 3.5.dp, highlight = highlight, shadow = shadow)
            .background(color, shape)
    }
    Box(
        modifier = modifier
            .size(size)
            .then(decorated)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** 胶囊拟态按钮，带文字。 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Neu.Background,
    highlight: Color = Neu.Highlight,
    shadow: Color = Neu.Shadow,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = NeuShapes.pill
    val decorated = if (pressed && enabled) {
        Modifier.background(color, shape).neuInset(shape, stroke = 2.5.dp, highlight = highlight, shadow = shadow)
    } else {
        Modifier.neuRaised(shape, blur = 9.dp, offset = 3.5.dp, highlight = highlight, shadow = shadow)
            .background(color, shape)
    }
    Row(
        modifier = modifier
            .then(decorated)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

/**
 * 拟态滑杆：凹陷轨道 + 已播放进度 + 凸起圆形滑块。
 * 自己实现是因为 Material 的 Slider 是扁平风格，放进拟态界面会很突兀。
 */
@Composable
fun NeuSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Neu.Accent,
    trackColor: Color = Neu.Background,
    highlight: Color = Neu.Highlight,
    shadow: Color = Neu.Shadow,
) {
    val safeValue = value.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { onValueChangeFinished() },
                    onDragCancel = { onValueChangeFinished() },
                ) { change, _ ->
                    onValueChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 22.dp
        val trackHeight = 10.dp

        // 凹陷轨道
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(NeuShapes.pill)
                .background(trackColor)
                .neuInset(NeuShapes.pill, stroke = 2.dp, highlight = highlight, shadow = shadow)
        ) {
            // 已播放进度
            Box(
                Modifier
                    .fillMaxWidth(safeValue)
                    .height(trackHeight)
                    .background(
                        Brush.horizontalGradient(listOf(accent.copy(alpha = 0.75f), accent)),
                        NeuShapes.pill,
                    )
            )
        }

        // 凸起滑块
        if (enabled) {
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * safeValue)
                    .size(thumb)
                    .neuRaised(NeuShapes.pill, blur = 7.dp, offset = 2.5.dp, highlight = highlight, shadow = shadow)
                    .background(trackColor, NeuShapes.pill)
                    .padding(6.dp)
                    .background(accent, NeuShapes.pill)
            )
        }
    }
}
