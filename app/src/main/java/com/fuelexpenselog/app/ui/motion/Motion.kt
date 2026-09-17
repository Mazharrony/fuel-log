package com.fuelexpenselog.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion here is restrained and functional: the splash, and state feedback on
 * the entry screens. No parallax, no shared-element transitions, no bounce
 * beyond the single needle overshoot on the splash.
 *
 * Compose already collapses tween durations when the system animator scale is
 * zero. What it does NOT scale is delay(), which is wall-clock - so a staggered
 * animation built out of delays still makes a user who turned animations off sit
 * and wait. Stagger is therefore expressed as an offset inside one animation's
 * progress mapping, never as a delay.
 */
val LocalMotionScale: ProvidableCompositionLocal<Float> = compositionLocalOf { 1f }

@Composable
fun rememberSystemMotionScale(): Float {
    val context = LocalContext.current
    return runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
    }.getOrDefault(1f)
}

/** Everything is instant at scale 0, including the splash, which becomes a static frame. */
@Composable
fun <T> motionTween(
    durationMillis: Int,
    easing: Easing = StandardEasing,
): FiniteAnimationSpec<T> {
    val scale = LocalMotionScale.current
    return if (scale <= 0f) snap() else tween(
        durationMillis = (durationMillis * scale).toInt().coerceAtLeast(1),
        easing = easing,
    )
}

/** The design language uses one deceleration curve for almost everything. */
val StandardEasing: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** The single overshoot in the product, on the splash needle. */
val NeedleEasing: Easing = CubicBezierEasing(0.34f, 1.1f, 0.3f, 1f)

val EaseOut: Easing = LinearOutSlowInEasing

object Durations {
    /** Screen transitions: fade-through, no slide. Back is the same, reversed. */
    const val SCREEN = 220

    /** Full-tank toggle knob, and its row background crossfade. */
    const val TOGGLE = 140

    /** The yellow action half compressing to 96% on press. */
    const val PRESS = 90

    /** Chart bars growing from the baseline. */
    const val CHART = 420

    /** Per-bar offset inside the chart animation - an offset, not a delay. */
    const val CHART_STAGGER = 30

    /** A warning block expanding. Must not move focus out of the field. */
    const val WARNING = 160
}
