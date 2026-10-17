package tv.own.owntv.features.sport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import tv.own.owntv.core.german4k.German4kPaarung
import tv.own.owntv.core.german4k.German4kSpiel
import tv.own.owntv.core.german4k.vereinsInitialen
import tv.own.owntv.ui.theme.OwnTVTheme

/** German4K 3.2 (C5): Markenfarben des Platzhalters — dunkles Lila als Fläche, helles Lila als Schrift/Rand. */
private val WappenGrund = Color(0xFF140D22)
private val WappenLila = Color(0xFFA274FB)

/** Zustand eines Wappens: lädt noch, ist da, oder es gibt keins (null-URL bzw. Ladefehler). */
private enum class WappenZustand { LAEDT, DA, KEINS }

/**
 * German4K 3.2 (C5): Vereinswappen in fester Größe [groesse] — nie fokussierbar, damit sich am D-Pad nichts
 * ändert, und immer gleich groß, damit die Karte beim Nachladen nicht springt.
 *
 * Geladen wird über den App-weiten Coil-Loader (`OwnTVApp.newImageLoader`): dieselbe OkHttp-Leitung,
 * derselbe Speicher- und Plattencache wie Poster und Senderlogos. Die Wappen-URLs ändern sich nie (neues
 * Wappen = neue Datei auf dem CDN), deshalb reicht der Standard — Coil hält die Datei im Plattencache, bis
 * der Platz gebraucht wird, ohne die Cache-Header zu fragen.
 *
 * Ohne URL oder bei Ladefehler: runder Platzhalter mit 1–3 Initialen ([vereinsInitialen]) — nie ein
 * kaputtes Bild-Symbol. Während des Ladens nur die leere Scheibe, damit ein schnell geladenes Wappen nicht
 * erst als „EF“ aufblitzt.
 */
@Composable
internal fun VereinsWappen(url: String?, name: String, groesse: Dp, modifier: Modifier = Modifier) {
    var zustand by remember(url) { mutableStateOf(if (url == null) WappenZustand.KEINS else WappenZustand.LAEDT) }
    Box(modifier.size(groesse), contentAlignment = Alignment.Center) {
        if (zustand != WappenZustand.DA) WappenPlatzhalter(name, groesse, mitText = zustand == WappenZustand.KEINS)
        if (url != null && zustand != WappenZustand.KEINS) {
            val ctx = LocalPlatformContext.current
            val request = remember(url, ctx) { ImageRequest.Builder(ctx).data(url).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                onState = { s ->
                    when (s) {
                        is AsyncImagePainter.State.Success -> zustand = WappenZustand.DA
                        is AsyncImagePainter.State.Error -> zustand = WappenZustand.KEINS
                        else -> Unit
                    }
                },
            )
        }
    }
}

/**
 * German4K 3.2 (C6): kleines Wettbewerbslogo (Bundesliga, Champions League …) vor dem Wettbewerbsnamen.
 *
 * Bewusst KEIN Platzhalter: ohne URL kommt gar nichts in die Komposition — die Karte sieht dann aus wie in
 * 3.1, nichts wird reserviert. Mit URL steht sofort ein Kasten in fester Größe [groesse] (auch während des
 * Ladens), damit der Name daneben beim Nachladen nicht seitwärts springt.
 * Gleicher App-weiter Coil-Loader wie [VereinsWappen]; nie fokussierbar (reines Bild, kein Klickziel).
 * [abstand] folgt nur, wenn ein Logo da ist — so entsteht ohne Logo auch kein Leerraum.
 */
@Composable
internal fun WettbewerbsLogo(url: String?, groesse: Dp, abstand: Dp = 6.dp) {
    // Ladefehler (Datei fehlt auf dem CDN): Platz wieder freigeben statt dauerhaft ein Loch vor dem Namen
    // zu lassen — ein einmaliges Nachrücken nur im Fehlerfall, der Normalfall springt nie.
    var fehler by remember(url) { mutableStateOf(false) }
    if (url == null || fehler) return
    val ctx = LocalPlatformContext.current
    val request = remember(url, ctx) { ImageRequest.Builder(ctx).data(url).build() }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(groesse),
        onState = { s -> if (s is AsyncImagePainter.State.Error) fehler = true },
    )
    Spacer(Modifier.width(abstand))
}

@Composable
private fun WappenPlatzhalter(name: String, groesse: Dp, mitText: Boolean) {
    val initialen = remember(name) { vereinsInitialen(name) }
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(WappenGrund)
            .border(width = groesse * RAND_ANTEIL, color = WappenLila.copy(alpha = 0.45f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (mitText && initialen.isNotEmpty()) {
            // Schrift wächst mit der Scheibe; drei Buchstaben brauchen etwas weniger als zwei.
            // In dp gerechnet (toSp teilt die Schriftskalierung heraus): bei großer Systemschrift liefe der
            // Text sonst aus der festen Scheibe.
            val anteil = if (initialen.length >= 3) 0.28f else 0.36f
            val schrift = with(LocalDensity.current) { (groesse * anteil).toSp() }
            Text(
                initialen,
                color = WappenLila,
                fontWeight = FontWeight.Bold,
                fontSize = schrift,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Verkleinerung eines Vereinsnamens, der nicht in zwei Zeilen passt. */
private const val KLEIN_FAKTOR = 0.85f

/** Randstärke des Platzhalters relativ zur Größe — bei 24 dp knapp 1 dp, bei 88 dp gut 3 dp. */
private const val RAND_ANTEIL = 0.04f

/** German4K 3.2 (C5): Hat der Eintrag zwei Vereine? Konferenzen und Einträge ohne Heim/Gast bleiben beim Titel. */
internal fun German4kSpiel.hatPaarung(): Boolean = !istKonferenz && heim.isNotBlank() && gast.isNotBlank()

/**
 * German4K 3.2 (C5): „Wappen über Name – Wappen über Name“ wie im Entwurf (Hub-Karte, Spielseite).
 * Beide Hälften gleich breit; lange Namen brechen in höchstens zwei Zeilen um und werden dann gekürzt,
 * die Wappen bleiben fest — so verschiebt ein langer Name nie den Gegner oder die Fußzeile.
 */
@Composable
internal fun PaarungMitWappen(
    spiel: German4kSpiel,
    wappen: Dp,
    namenStil: TextStyle,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        VereinSpalte(spiel.heim, spiel.heimLogo, wappen, namenStil, Modifier.weight(1f))
        Box(Modifier.height(wappen).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(PAARUNG_STRICH, style = namenStil, color = OwnTVTheme.colors.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
        VereinSpalte(spiel.gast, spiel.gastLogo, wappen, namenStil, Modifier.weight(1f))
    }
}

@Composable
private fun VereinSpalte(name: String, logo: String?, wappen: Dp, namenStil: TextStyle, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        VereinsWappen(logo, name, wappen)
        Spacer(Modifier.height(6.dp))
        // Passt ein langer Name („Borussia Mönchengladbach“) nicht in zwei Zeilen, erst kleiner, dann kürzen —
        // wie der Wettbewerb in der Kopfzeile der Karte.
        var klein by remember(name, namenStil) { mutableStateOf(false) }
        Text(
            name,
            style = if (klein) namenStil.copy(fontSize = namenStil.fontSize * KLEIN_FAKTOR, lineHeight = namenStil.lineHeight * KLEIN_FAKTOR) else namenStil,
            color = OwnTVTheme.colors.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { r -> if (!klein && r.hasVisualOverflow) klein = true },
        )
    }
}

/**
 * German4K 3.2 (C5): kompakte Paarung für die Startseiten-Kachel — zwei Zeilen „Wappen Name“ wie eine
 * Anzeigetafel; die Kachel bleibt so hoch wie vorher.
 */
@Composable
internal fun PaarungKompakt(spiel: German4kSpiel, wappen: Dp, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        VereinZeile(spiel.heim, spiel.heimLogo, wappen)
        VereinZeile(spiel.gast, spiel.gastLogo, wappen)
    }
}

@Composable
private fun VereinZeile(name: String, logo: String?, wappen: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VereinsWappen(logo, name, wappen)
        Spacer(Modifier.width(8.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = OwnTVTheme.colors.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * German4K 3.2 (C5): Paarungen der Konferenz-Karte mit kleinen Wappen, fließend umbrochen und auf
 * [zeilen] Zeilen begrenzt (feste Zeilenhöhe + Abschneiden — so steht nie eine halbe dritte Zeile da;
 * wie viele Spiele es sind, sagt der Kartenkopf).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun KonferenzPaarungen(paarungen: List<German4kPaarung>, wappen: Dp, zeilen: Int, modifier: Modifier = Modifier) {
    val zeile = wappen + 2.dp
    val abstand = 4.dp
    FlowRow(
        modifier.fillMaxWidth().heightIn(max = zeile * zeilen + abstand * (zeilen - 1)).clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(abstand),
    ) {
        paarungen.forEach { p ->
            Row(Modifier.height(zeile), verticalAlignment = Alignment.CenterVertically) {
                VereinsWappen(p.heimLogo, p.heim, wappen)
                Spacer(Modifier.width(5.dp))
                KonferenzName(p.heim)
                KonferenzName(PAARUNG_STRICH_MIT_LEERRAUM)
                VereinsWappen(p.gastLogo, p.gast, wappen)
                Spacer(Modifier.width(5.dp))
                KonferenzName(p.gast)
            }
        }
    }
}

@Composable
private fun KonferenzName(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant, maxLines = 1)
}

/** Strich zwischen Heim und Gast — kein Wort, also nichts zu übersetzen. */
private const val PAARUNG_STRICH = "–"
private const val PAARUNG_STRICH_MIT_LEERRAUM = " – "
