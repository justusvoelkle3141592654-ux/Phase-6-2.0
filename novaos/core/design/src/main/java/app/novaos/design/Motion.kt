package app.novaos.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import app.novaos.model.AnimArea
import app.novaos.model.AnimationSpec
import app.novaos.model.AnimationStyle

/** Übersetzt die Animationseinstellungen in Compose-Animationen. */
object NovaMotion {
    private val liquidEasing = CubicBezierEasing(0.2f, 0.9f, 0.1f, 1f)
    private val fluidEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
    private val dotsEasing = CubicBezierEasing(0.7f, 0f, 0.3f, 1f)

    fun <T> spec(m: AnimationSpec, area: AnimArea): FiniteAnimationSpec<T> {
        val d = m.scaledDuration()
        return when (m.styleFor(area)) {
            AnimationStyle.MINIMAL -> tween((d * 0.55f).toInt(), easing = LinearOutSlowInEasing)
            AnimationStyle.SMOOTH -> tween(d, easing = FastOutSlowInEasing)
            AnimationStyle.LIQUID -> tween((d * 1.15f).toInt(), easing = liquidEasing)
            AnimationStyle.ELASTIC -> spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow * m.speed.coerceIn(0.5f, 3f))
            AnimationStyle.NOTHING_DOTS -> tween(d, easing = dotsEasing)
            AnimationStyle.FLUID -> spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow * m.speed.coerceIn(0.5f, 3f))
            AnimationStyle.CLASSIC_ANDROID -> tween((d * 0.8f).toInt(), easing = FastOutSlowInEasing)
        }
    }

    fun enter(m: AnimationSpec, area: AnimArea): EnterTransition = when (m.styleFor(area)) {
        AnimationStyle.MINIMAL -> fadeIn(spec(m, area))
        AnimationStyle.SMOOTH, AnimationStyle.CLASSIC_ANDROID -> fadeIn(spec(m, area)) + slideInVertically(spec<IntOffset>(m, area)) { it / 6 }
        AnimationStyle.LIQUID, AnimationStyle.FLUID -> fadeIn(spec(m, area)) + scaleIn(spec(m, area), initialScale = 0.92f) + slideInVertically(spec<IntOffset>(m, area)) { it / 10 }
        AnimationStyle.ELASTIC -> fadeIn(tween(m.scaledDuration() / 2)) + scaleIn(spec(m, area), initialScale = 0.8f)
        AnimationStyle.NOTHING_DOTS -> fadeIn(spec(m, area)) + scaleIn(spec(m, area), initialScale = 0.97f)
    }

    fun exit(m: AnimationSpec, area: AnimArea): ExitTransition = when (m.styleFor(area)) {
        AnimationStyle.MINIMAL -> fadeOut(spec(m, area))
        AnimationStyle.SMOOTH, AnimationStyle.CLASSIC_ANDROID -> fadeOut(spec(m, area)) + slideOutVertically(spec<IntOffset>(m, area)) { it / 6 }
        AnimationStyle.LIQUID, AnimationStyle.FLUID -> fadeOut(spec(m, area)) + scaleOut(spec(m, area), targetScale = 0.94f)
        AnimationStyle.ELASTIC -> fadeOut(tween(m.scaledDuration() / 2)) + scaleOut(tween(m.scaledDuration() / 2), targetScale = 0.85f)
        AnimationStyle.NOTHING_DOTS -> fadeOut(spec(m, area)) + scaleOut(spec(m, area), targetScale = 0.98f)
    }

    /** Fortschrittsdauer für punktbasierte Ein-/Ausblendungen. */
    fun durationMs(m: AnimationSpec, area: AnimArea): Int = when (m.styleFor(area)) {
        AnimationStyle.MINIMAL -> (m.scaledDuration() * 0.55f).toInt()
        AnimationStyle.LIQUID -> (m.scaledDuration() * 1.15f).toInt()
        else -> m.scaledDuration()
    }
}
