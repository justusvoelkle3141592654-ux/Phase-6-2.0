package app.novaos.ui.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.novaos.model.GestureClassifier
import app.novaos.model.GestureType

/**
 * Nova Gesture Engine: erkennt Wisch- und Zwei-Finger-Gesten, ohne Ereignisse zu verbrauchen
 * (Seitenwechsel, Antippen und Widgets funktionieren weiter). Gesten, die in den
 * System-Gestenbereichen von Android beginnen, werden ignoriert.
 */
fun Modifier.launcherGestures(
    enabled: Boolean,
    atFirstPage: () -> Boolean,
    atLastPage: () -> Boolean,
    onGesture: (GestureType) -> Unit,
): Modifier = composed {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val insets = WindowInsets.systemGestures
    val bottom = insets.getBottom(density)
    val left = insets.getLeft(density, dir)
    val right = insets.getRight(density, dir)
    val top = insets.getTop(density)
    val minDist = with(density) { 72.dp.toPx() }
    val latest by rememberUpdatedState(onGesture)
    val en by rememberUpdatedState(enabled)
    val first by rememberUpdatedState(atFirstPage)
    val last by rememberUpdatedState(atLastPage)

    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val start = down.position
            val inSystemZone = start.y > size.height - bottom || start.x < left || start.x > size.width - right || start.y < top
            var maxPointers = 1
            var startDistance = 0f
            var endDistance = 0f
            var lastPrimary = start
            val starts = mutableMapOf(down.id to start)
            val lasts = mutableMapOf(down.id to start)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { ch ->
                    if (ch.id !in starts) starts[ch.id] = ch.position
                    lasts[ch.id] = ch.position
                }
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size > maxPointers) {
                    maxPointers = pressed.size
                    if (pressed.size >= 2) startDistance = (pressed[0].position - pressed[1].position).getDistance()
                }
                if (pressed.size >= 2) endDistance = (pressed[0].position - pressed[1].position).getDistance()
                event.changes.firstOrNull { it.id == down.id }?.let { lastPrimary = it.position }
                if (pressed.isEmpty()) break
            }
            if (!en || inSystemZone) return@awaitEachGesture
            if (maxPointers >= 2 && startDistance > 0f && endDistance in 1f..(startDistance * 0.65f)) {
                latest(GestureType.PINCH_IN); return@awaitEachGesture
            }
            val delta: Offset = if (maxPointers >= 2) {
                val ds = starts.keys.mapNotNull { k -> lasts[k]?.minus(starts.getValue(k)) }
                Offset(ds.map { it.x }.average().toFloat(), ds.map { it.y }.average().toFloat())
            } else lastPrimary - start
            val type = GestureClassifier.classifySwipe(delta.x, delta.y, maxPointers, minDist) ?: return@awaitEachGesture
            when (type) {
                GestureType.SWIPE_LEFT -> if (last()) latest(type)
                GestureType.SWIPE_RIGHT -> if (first()) latest(type)
                else -> latest(type)
            }
        }
    }
}
