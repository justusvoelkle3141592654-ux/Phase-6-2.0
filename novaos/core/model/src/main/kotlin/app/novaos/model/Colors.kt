package app.novaos.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Farben werden in der Konfiguration als HEX-Text gespeichert ("#RRGGBB" oder "#AARRGGBB"),
 * damit Backups und exportierte Themes lesbar bleiben.
 */
object ColorUtil {

    /** Wandelt "#RGB", "#RRGGBB" oder "#AARRGGBB" in ARGB um. Gibt null bei ungültiger Eingabe. */
    fun parseHex(input: String?): Long? {
        if (input == null) return null
        var s = input.trim().removePrefix("#")
        if (s.length == 3) s = s.map { "$it$it" }.joinToString("")
        if (s.length == 6) s = "FF$s"
        if (s.length != 8) return null
        if (!s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return s.toLong(16)
    }

    fun isValidHex(input: String?): Boolean = parseHex(input) != null

    fun toHex(argb: Long, withAlpha: Boolean = alpha(argb) != 255): String {
        val a = alpha(argb)
        val rgb = argb and 0xFFFFFF
        return if (withAlpha) "#%02X%06X".format(a, rgb) else "#%06X".format(rgb)
    }

    fun argb(a: Int, r: Int, g: Int, b: Int): Long =
        ((a.coerceIn(0, 255).toLong() shl 24) or (r.coerceIn(0, 255).toLong() shl 16) or
            (g.coerceIn(0, 255).toLong() shl 8) or b.coerceIn(0, 255).toLong())

    fun alpha(c: Long) = ((c shr 24) and 0xFF).toInt()
    fun red(c: Long) = ((c shr 16) and 0xFF).toInt()
    fun green(c: Long) = ((c shr 8) and 0xFF).toInt()
    fun blue(c: Long) = (c and 0xFF).toInt()

    fun withAlpha(c: Long, alpha: Float): Long =
        argb((alpha.coerceIn(0f, 1f) * 255).roundToInt(), red(c), green(c), blue(c))

    /** Relative Leuchtdichte nach WCAG 2.1. */
    fun luminance(c: Long): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(red(c)) + 0.7152 * ch(green(c)) + 0.0722 * ch(blue(c))
    }

    /** Kontrastverhältnis nach WCAG 2.1 (1.0 bis 21.0). */
    fun contrastRatio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    fun blend(a: Long, b: Long, t: Float): Long {
        val k = t.coerceIn(0f, 1f)
        fun mix(x: Int, y: Int) = (x + (y - x) * k).roundToInt()
        return argb(mix(alpha(a), alpha(b)), mix(red(a), red(b)), mix(green(a), green(b)), mix(blue(a), blue(b)))
    }

    /** Liefert Schwarz oder Weiß, je nachdem was auf [background] besser lesbar ist. */
    fun readableOn(background: Long): Long =
        if (contrastRatio(background, 0xFFFFFFFF) >= contrastRatio(background, 0xFF000000)) 0xFFFFFFFF else 0xFF000000

    /** Passt den Kontrast einer Vordergrundfarbe zum Hintergrund an (factor > 1 erhöht Kontrast). */
    fun adjustContrast(foreground: Long, background: Long, factor: Float): Long {
        if (factor == 1f) return foreground
        val target = readableOn(background)
        return if (factor > 1f) blend(foreground, target, (factor - 1f).coerceIn(0f, 1f))
        else blend(foreground, background, (1f - factor).coerceIn(0f, 0.8f))
    }
}
