package app.novaos.design

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.novaos.model.FontChoice

/**
 * Schriftfamilien. Alle mitgelieferten Schriften stehen unter der SIL Open Font License (siehe docs/licenses).
 * Variable Schriften werden mit passenden Gewichtsachsen geladen.
 */
@OptIn(ExperimentalTextApi::class)
object NovaFonts {
    private val weights = listOf(200, 300, 400, 500, 600, 700, 800)

    private fun variable(res: Int, min: Int = 300, max: Int = 700): FontFamily = FontFamily(
        weights.map { w ->
            val v = w.coerceIn(min, max)
            Font(res, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(v)))
        },
    )

    val spaceGrotesk: FontFamily = variable(R.font.space_grotesk, 300, 700)
    val inter: FontFamily = variable(R.font.inter, 100, 900)
    val jetbrainsMono: FontFamily = variable(R.font.jetbrains_mono, 100, 800)
    val doto: FontFamily = variable(R.font.doto, 100, 900)
    val spaceMono: FontFamily = FontFamily(
        Font(R.font.space_mono_regular, FontWeight.Normal),
        Font(R.font.space_mono_bold, FontWeight.Bold),
    )

    fun family(choice: FontChoice): FontFamily = when (choice) {
        FontChoice.SPACE_GROTESK -> spaceGrotesk
        FontChoice.INTER -> inter
        FontChoice.SPACE_MONO -> spaceMono
        FontChoice.JETBRAINS_MONO -> jetbrainsMono
        FontChoice.DOTO -> doto
        // Die eigene Punktmatrix wird gezeichnet; für Fließtext-Fallbacks wird Doto verwendet.
        FontChoice.NOVA_DOT -> doto
        FontChoice.SYSTEM_SANS -> FontFamily.SansSerif
        FontChoice.SYSTEM_SERIF -> FontFamily.Serif
        FontChoice.SYSTEM_MONO -> FontFamily.Monospace
    }
}
