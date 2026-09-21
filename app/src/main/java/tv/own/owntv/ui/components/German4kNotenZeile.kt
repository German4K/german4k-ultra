package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import tv.own.owntv.R
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.german4k.German4kNoten
import tv.own.owntv.ui.theme.OwnTVTheme

// German4K: Bewertungen mit den Zeichen der Seiten, von denen sie stammen (Kundenwunsch 21.09.2026,
// „wie in UHF"). Die Zeichen sind nachgebaut (Farbe, Form, Schriftzug), keine Bilddateien: nichts
// zu laden, gestochen scharf auf jedem Fernseher, und kein fremdes Bild im APK.

private val ImdbGelb = Color(0xFFF5C518)
private val TmdbDunkel = Color(0xFF0D253F)
private val TmdbGruen = Color(0xFF90CEA1)
private val TmdbBlau = Color(0xFF01B4E4)
private val RtRot = Color(0xFFFA320A)
private val RtGruen = Color(0xFF0AC855)
private val TraktRot = Color(0xFFED1C24)
private val LbOrange = Color(0xFFFF8000)
private val LbGruen = Color(0xFF00E054)
private val LbBlau = Color(0xFF40BCF4)

/** Eine Nachkommastelle in der Schreibweise des Geräts — „7,4" auf einem deutschen Fernseher. */
@Composable
fun german4kNote(wert: Double): String = stringResource(R.string.common_rating, wert)

/** Das gelbe IMDb-Schild. */
@Composable
fun ImdbZeichen(hoehe: Dp = 18.dp) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(hoehe * 0.18f))
            .background(ImdbGelb)
            .size(width = hoehe * 1.9f, height = hoehe),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.g4k_marke_imdb),
            style = TextStyle(fontSize = (hoehe.value * 0.62f).sp, fontWeight = FontWeight.Black, color = Color.Black, letterSpacing = (-0.3).sp),
        )
    }
}

@Composable
private fun TmdbZeichen(hoehe: Dp) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(hoehe * 0.25f))
            .background(TmdbDunkel)
            .size(width = hoehe * 2.3f, height = hoehe),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(hoehe * 0.2f))
                .background(Brush.horizontalGradient(listOf(TmdbGruen, TmdbBlau)))
                .padding(horizontal = hoehe * 0.2f),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.g4k_marke_tmdb), style = TextStyle(fontSize = (hoehe.value * 0.55f).sp, fontWeight = FontWeight.Black, color = TmdbDunkel))
        }
    }
}

/** Tomate (frisch, ab 60 %) oder grüner Klecks (faul) — wie bei Rotten Tomatoes. */
@Composable
private fun RtZeichen(prozent: Int, hoehe: Dp) {
    val frisch = prozent >= 60
    Canvas(Modifier.size(hoehe)) {
        val r = size.minDimension / 2f
        if (frisch) {
            drawCircle(RtRot, radius = r * 0.86f, center = Offset(size.width / 2f, size.height * 0.56f))
            val blatt = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.30f)
                lineTo(size.width * 0.28f, size.height * 0.08f)
                lineTo(size.width * 0.46f, size.height * 0.18f)
                lineTo(size.width * 0.5f, 0f)
                lineTo(size.width * 0.56f, size.height * 0.18f)
                lineTo(size.width * 0.74f, size.height * 0.08f)
                close()
            }
            drawPath(blatt, RtGruen)
        } else {
            drawCircle(RtGruen, radius = r * 0.72f)
            for (i in 0 until 6) {
                val w = Math.toRadians(i * 60.0 + 15)
                drawCircle(RtGruen, radius = r * 0.22f, center = Offset(center.x + (r * 0.8f * Math.cos(w)).toFloat(), center.y + (r * 0.8f * Math.sin(w)).toFloat()))
            }
        }
    }
}

/** Metacritic: die Zahl im Feld, grün ab 61, gelb ab 40, darunter rot. */
@Composable
private fun MetacriticZeichen(wert: Int, hoehe: Dp) {
    val farbe = when {
        wert >= 61 -> Color(0xFF00CE7A)
        wert >= 40 -> Color(0xFFFFBD3F)
        else -> Color(0xFFFF6874)
    }
    Box(
        Modifier.clip(RoundedCornerShape(hoehe * 0.15f)).background(farbe).size(width = hoehe * 1.45f, height = hoehe),
        contentAlignment = Alignment.Center,
    ) {
        Text(wert.toString(), style = TextStyle(fontSize = (hoehe.value * 0.6f).sp, fontWeight = FontWeight.Black, color = Color.Black))
    }
}

@Composable
private fun LetterboxdZeichen(hoehe: Dp) {
    Canvas(Modifier.size(width = hoehe * 1.7f, height = hoehe)) {
        val r = size.height * 0.3f
        val y = size.height / 2f
        drawCircle(LbOrange, r, Offset(r, y))
        drawCircle(LbGruen, r, Offset(size.width / 2f, y))
        drawCircle(LbBlau, r, Offset(size.width - r, y))
    }
}

@Composable
private fun TraktZeichen(hoehe: Dp) {
    Canvas(Modifier.size(hoehe)) {
        drawCircle(TraktRot)
        drawCircle(Color.White, radius = size.minDimension * 0.34f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.08f))
        drawRect(Color.White, topLeft = Offset(size.width * 0.44f, size.height * 0.3f), size = Size(size.width * 0.12f, size.height * 0.4f))
    }
}

/** Ein Eintrag: Zeichen, Zahl, darunter nichts — kompakt genug für eine Zeile im Seitenfeld. */
@Composable
private fun Eintrag(zeichen: @Composable () -> Unit, wert: String, zusatz: String? = null) {
    val colors = OwnTVTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        zeichen()
        Spacer(Modifier.width(6.dp))
        Text(wert, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
        if (zusatz != null) {
            Spacer(Modifier.width(3.dp))
            Text(zusatz, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

/** Stimmen kurz: „2,7 Mio.", „123 Tsd." — die genaue Zahl liest niemand vom Sofa aus. */
@Composable
private fun stimmenKurz(n: Int): String = when {
    n >= 1_000_000 -> stringResource(R.string.g4k_stimmen_mio, n / 1_000_000.0)
    n >= 1_000 -> stringResource(R.string.g4k_stimmen_tsd, n / 1_000)
    else -> stringResource(R.string.g4k_stimmen, n)
}

/**
 * Die Notenzeile der Detailansicht: IMDb zuerst, dann was es sonst gibt. Bricht um, wenn das Feld
 * schmal ist. Zeigt nichts, wenn es keine Noten gibt (fremder Server, unbekannter Titel).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun German4kNotenZeile(noten: German4kNoten?, modifier: Modifier = Modifier, hoehe: Dp = 20.dp) {
    if (noten == null || noten.leer) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        noten.imdb?.let { Eintrag({ ImdbZeichen(hoehe) }, german4kNote(it), noten.imdbStimmen?.let { stimmenKurz(it) }) }
        noten.tmdb?.let { Eintrag({ TmdbZeichen(hoehe) }, german4kNote(it)) }
        noten.rt?.let { Eintrag({ RtZeichen(it, hoehe) }, stringResource(R.string.g4k_prozent, it)) }
        noten.metacritic?.let { Eintrag({ MetacriticZeichen(it, hoehe) }, stringResource(R.string.g4k_marke_metacritic)) }
        noten.letterboxd?.let { Eintrag({ LetterboxdZeichen(hoehe) }, german4kNote(it)) }
        noten.trakt?.let { Eintrag({ TraktZeichen(hoehe) }, stringResource(R.string.g4k_prozent, it)) }
    }
}
