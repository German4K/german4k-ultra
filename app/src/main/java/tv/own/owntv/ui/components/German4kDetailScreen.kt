package tv.own.owntv.ui.components

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.german4k.German4kDarsteller
import tv.own.owntv.core.german4k.German4kDetails
import tv.own.owntv.core.german4k.German4kFassung
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

// German4K: Die Detailseite, die vor dem Abspielen aufgeht (Kundenwunsch 21.09.2026). Ein Querbild
// statt des Plakats, darunter Noten, Knoepfe, Handlung, Regie & Besetzung und weitere Fassungen.
// Bewusst schlank: drei Schriftgroessen, ein Eckradius, viel Luft — vom Sofa aus soll man in einer
// Sekunde sehen, was das ist und welcher Knopf abspielt.

/** Ein Eckradius fuer beide Kachelarten, damit die Seite nicht nach Flickenteppich aussieht. */
private val Ecke = RoundedCornerShape(Dimens.CornerSmall)

/** Kachelmasse der Besetzungsreihe. */
private val PersonBreite = 110.dp
private val PersonFoto = 72.dp

/** Zwischen den Teilen der Meta-Zeile — ein Zeichen, keine uebersetzbare Wendung. */
private const val TRENNER = " · "

/** Eine Zeile Handlung pro Druck, wenn die Seite selbst scrollen muss — wie im TMDB-Fenster. */
private const val SCROLL_SCHRITT = 260f

/** ~10 Bilder: lange genug, bis die Seite wirklich haengt, kurz genug, um niemandem im Weg zu sein. */
private const val FOKUS_VERSUCHE = 10

/**
 * Was die Seite zeigt. [details] == null heisst: der Titel ist bekannt, der Server antwortet noch —
 * die Knoepfe funktionieren trotzdem, Abspielen wartet nie auf den Detailaufruf.
 *
 * [schluessel] ist die Kennung des gezeigten Titels und muss sich aendern, sobald die Seite einen
 * ANDEREN Titel zeigt — der Aufrufer setzt dafuer die `remoteId` des Streams ein (bei einem Sprung
 * ueber „Weitere Fassungen" also die `remoteId` der gewaehlten Fassung); fehlt eine, tut es
 * `"${'$'}serie:${'$'}titel"`. Daran haengen Scrollstand, Fokus und der Trailer: bleibt der
 * Schluessel gleich, bleibt die Seite stehen, waehrend `details` nachlaedt; wechselt er, faengt die
 * Seite oben an, der erste Knopf bekommt den Fokus und ein laufender Trailer hoert auf.
 */
@Immutable
data class German4kDetailUi(
    val schluessel: String,
    val titel: String,
    val plakat: String?,
    val details: German4kDetails?,
    val serie: Boolean,
    val favorit: Boolean,
    val fortsetzenMs: Long?,
)

/**
 * Die Detailseite. Vollbild ueber der Uebersicht, kein Fensterrahmen.
 *
 * Fokus: der erste Knopf bekommt ihn beim Oeffnen; hoch/runter wandert zwischen Knopfzeile,
 * Besetzung und Fassungen. Das Scrollen macht Compose selbst — `focusable()` (in [FocusableSurface])
 * meldet sich beim `verticalScroll` der Spalte an, sobald eine Kachel den Fokus bekommt. Gibt es
 * weder Besetzung noch Fassungen, ist die Knopfzeile das einzige Fokusziel; dann scrollt die Seite
 * hoch/runter selbst, damit die Handlung nicht unerreichbar unter der Falz liegt.
 */
@Composable
fun German4kDetailScreen(
    ui: German4kDetailUi,
    onAbspielen: (startMs: Long) -> Unit,
    onFavorit: () -> Unit,
    onFolgen: () -> Unit,
    onPerson: (German4kDarsteller) -> Unit,
    onFassung: (German4kFassung) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val details = ui.details

    // Alles, was zum gezeigten Titel gehoert, haengt am Schluessel: waehlt jemand eine andere
    // Fassung, faengt die Seite oben an, der Fokus geht zurueck auf den ersten Knopf und ein
    // laufender Trailer hoert auf. Ohne Schluessel behielte die neue Fassung den alten Scrollstand.
    val scroll = rememberSaveable(ui.schluessel, saver = ScrollState.Saver) { ScrollState(0) }
    val ersterKnopf = remember(ui.schluessel) { FocusRequester() }
    var trailerLaeuft by remember(ui.schluessel) { mutableStateOf(false) }
    // Merkt sich, dass der Trailer auf diesem Geraet nicht geht (keine brauchbare WebView, gesperrtes
    // Video). Dann verschwindet der Knopf, statt bei jedem Druck dasselbe Nichts zu zeigen.
    var trailerUnmoeglich by remember(ui.schluessel) { mutableStateOf(false) }

    // Reihenfolge ist Absicht: der zweite BackHandler gewinnt, solange er eingeschaltet ist. Zurueck
    // beendet also erst den Trailer und schliesst erst beim naechsten Druck die Seite.
    BackHandler { onExit() }
    BackHandler(enabled = trailerLaeuft) { trailerLaeuft = false }

    // Ein einzelner Versuch geht ins Leere, solange die Seite noch aufgeht — dann haette die Seite
    // gar kein Fokusziel und das D-Pad waere tot. Deshalb Bild fuer Bild nachfassen (wie FocusTrap).
    LaunchedEffect(ui.schluessel) {
        repeat(FOKUS_VERSUCHE) {
            withFrameNanos { }
            if (runCatching { ersterKnopf.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // Ohne Besetzung und ohne Fassungen (Nachtrag, Tafel-Line) sind die Knoepfe das einzige
    // Fokusziel der Seite: hoch/runter fuehren nirgendwohin, trapAllFocusExit() schluckt sie, und
    // Handlung wie Ladehinweis blieben unter der Falz unerreichbar. In dieser Lage — und nur da —
    // scrollt die Seite selbst, genau wie das TMDB-Fenster (MediaDetailsScreen). Sobald es eine
    // Reihe gibt, bleibt das D-Pad beim Fokus, sonst kaeme man in die Reihen nicht mehr hinein.
    val keinZielUnten = details?.besetzung.isNullOrEmpty() && details?.fassungen.isNullOrEmpty()
    val scope = rememberCoroutineScope()
    val onKey: (KeyEvent) -> Boolean = onKey@{ e ->
        if (!keinZielUnten || e.type != KeyEventType.KeyDown) return@onKey false
        when (e.key) {
            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(SCROLL_SCHRITT) }; true }
            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-SCROLL_SCHRITT) }; true }
            else -> false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .modalScrim()
            .onKeyEvent(onKey)
            .trapAllFocusExit()
            .focusGroup(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .verticalScroll(scroll),
        ) {
            Hero(
                ui = ui,
                trailerLaeuft = trailerLaeuft,
                onTrailerEnde = { trailerLaeuft = false },
                onTrailerUnmoeglich = { trailerUnmoeglich = true; trailerLaeuft = false },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Dimens.DetailSeitenRand, end = Dimens.DetailSeitenRand, top = 20.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Zeigt nichts, wenn es keine Noten gibt — dann faellt auch der Abstand weg.
                German4kNotenZeile(details?.noten, hoehe = 24.dp)

                Knopfzeile(
                    ui = ui,
                    ersterKnopf = ersterKnopf,
                    trailerMoeglich = details?.trailer != null && !trailerUnmoeglich,
                    onAbspielen = onAbspielen,
                    onFavorit = onFavorit,
                    onFolgen = onFolgen,
                    onTrailer = { trailerLaeuft = true },
                )

                val plot = details?.plot?.takeIf { it.isNotBlank() }
                when {
                    plot != null -> Text(
                        plot,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    details == null -> Text(
                        stringResource(R.string.g4k_detail_laedt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }

                BesetzungReihe(details?.besetzung.orEmpty(), onPerson)
                FassungenReihe(details?.fassungen.orEmpty(), onFassung)
            }
        }
    }
}

/**
 * Querbild mit weichem Verlauf in den Seitenhintergrund; Titel und Meta-Zeile sitzen im Fuss.
 * Solange der Trailer laeuft, ist das Feld reines Video: kein Verlauf, keine Schrift darueber —
 * jede Schicht darueber zwingt die WebView aus der Hardware-Ebene und kostet Bilder.
 */
@Composable
private fun Hero(
    ui: German4kDetailUi,
    trailerLaeuft: Boolean,
    onTrailerEnde: () -> Unit,
    onTrailerUnmoeglich: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val details = ui.details
    val trailer = details?.trailer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 16:6 statt 16:9 — auf 960x540 dp bleibt das Querbild damit 360 dp hoch, und die ersten
            // Zeilen der Handlung stehen beim Oeffnen schon im Bild statt unter der Falz.
            .aspectRatio(16f / 6f)
            .background(colors.surfaceContainerLowest),
    ) {
        if (trailerLaeuft && trailer != null) {
            HeroTrailer(trailer, onTrailerEnde, onTrailerUnmoeglich)
            return@Box
        }

        // Vor der Antwort gibt es nur das Plakat: hochkant, deshalb auf das Querformat beschnitten.
        val bild = details?.backdrop?.takeIf { it.isNotBlank() } ?: ui.plakat?.takeIf { it.isNotBlank() }
        if (bild != null) {
            AsyncImage(
                model = bild,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to colors.background),
            ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.DetailSeitenRand, end = Dimens.DetailSeitenRand, bottom = 12.dp),
        ) {
            Text(
                ui.titel,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = metaZeile(details)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(meta, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/**
 * Trailer im Querbild-Feld, im selben IFrame-Spieler wie [TrailerPlayerScreen].
 *
 * Bewusst OHNE aeusseren Ausweichweg: geht der Trailer nicht (Billiggeraet ohne brauchbare
 * System-WebView, gesperrtes Video), darf die Seite den Kunden nicht in eine fremde App werfen —
 * er wollte einen Film ansehen, nicht YouTube. Stattdessen faellt das Feld aufs Bild zurueck und
 * der Trailer-Knopf verschwindet fuer diesen Titel.
 */
@Composable
private fun HeroTrailer(schluessel: String, onEnde: () -> Unit, onUnmoeglich: () -> Unit) {
    val context = LocalContext.current
    var gescheitert by remember(schluessel) { mutableStateOf(false) }

    val spieler = remember(schluessel) {
        runCatching {
            YouTubePlayerView(context).apply {
                enableAutomaticInitialization = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // Reine Bildflaeche: der D-Pad-Fokus bleibt bei den Knoepfen der Seite.
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
        }.getOrNull()
    }

    if (spieler == null) {
        LaunchedEffect(schluessel) { onUnmoeglich() }
        return
    }
    LaunchedEffect(gescheitert) { if (gescheitert) onUnmoeglich() }

    val optionen = remember(context) {
        IFramePlayerOptions.Builder(context).controls(0).fullscreen(0).rel(0).ivLoadPolicy(3).ccLoadPolicy(0).build()
    }
    DisposableEffect(schluessel) {
        val zuhoerer = object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) = youTubePlayer.loadVideo(schluessel, 0f)
            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                gescheitert = true
            }
            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                if (state == PlayerConstants.PlayerState.ENDED) onEnde()
            }
        }
        runCatching { spieler.initialize(zuhoerer, optionen) }.onFailure { gescheitert = true }
        // Greift beim Trailerende genauso wie beim Verlassen der Seite — die WebView bleibt nie stehen.
        onDispose { runCatching { spieler.release() } }
    }

    AndroidView(factory = { spieler }, modifier = Modifier.fillMaxSize())
}

/**
 * „15.10.1999 · Drama · 122 Min." — Datum in der Schreibweise des Geraets, fehlende Teile fallen
 * ersatzlos weg (kein „· ·"), und ohne volles Datum reicht das Jahr.
 */
@Composable
private fun metaZeile(details: German4kDetails?): String {
    if (details == null) return ""
    val datum = details.datum.takeIf { it.isNotBlank() }?.let { datumLesbar(it) } ?: details.jahr.takeIf { it.isNotBlank() }
    val genre = details.genre.takeIf { it.isNotBlank() }
    val dauer = (details.dauerSek / 60).takeIf { it > 0 }?.let { stringResource(R.string.g4k_detail_dauer, it) }
    return listOfNotNull(datum, genre, dauer).joinToString(TRENNER)
}

private fun datumLesbar(iso: String): String? = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}.getOrNull()

/**
 * Eine Zeile Knoepfe. Bei einer Serie gibt es kein Abspielen — dort fuehrt „Folgen", und der Knopf
 * bekommt den Fokus und die kraeftige Farbe, damit klar ist, wo es weitergeht.
 */
@Composable
private fun Knopfzeile(
    ui: German4kDetailUi,
    ersterKnopf: FocusRequester,
    trailerMoeglich: Boolean,
    onAbspielen: (Long) -> Unit,
    onFavorit: () -> Unit,
    onFolgen: () -> Unit,
    onTrailer: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (ui.serie) {
            OwnTVButton(
                stringResource(R.string.g4k_detail_folgen),
                onClick = onFolgen,
                modifier = Modifier.focusRequester(ersterKnopf),
            )
        } else {
            val weiter = ui.fortsetzenMs
            val beschriftung = if (weiter != null) {
                stringResource(R.string.g4k_detail_fortsetzen, formatTimestamp(weiter))
            } else {
                stringResource(R.string.g4k_detail_abspielen)
            }
            OwnTVButton(
                beschriftung,
                onClick = { onAbspielen(weiter ?: 0L) },
                modifier = Modifier.focusRequester(ersterKnopf),
            )
            if (weiter != null) {
                OwnTVButton(
                    stringResource(R.string.g4k_detail_von_vorn),
                    onClick = { onAbspielen(0L) },
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
        OwnTVButton(
            stringResource(if (ui.favorit) R.string.g4k_detail_favorit_entfernen else R.string.g4k_detail_favorit),
            onClick = onFavorit,
            style = OwnTVButtonStyle.SECONDARY,
        )
        if (trailerMoeglich) {
            OwnTVButton(
                stringResource(R.string.g4k_detail_trailer),
                onClick = onTrailer,
                style = OwnTVButtonStyle.SECONDARY,
            )
        }
    }
}

/**
 * Regie und Besetzung in EINER Reihe, in der Reihenfolge des Servers (Regie zuerst). Ein duenner
 * Strich hinter dem letzten Regisseur zeigt den Uebergang, ohne eine zweite Ueberschrift zu kosten.
 */
@Composable
private fun BesetzungReihe(besetzung: List<German4kDarsteller>, onPerson: (German4kDarsteller) -> Unit) {
    if (besetzung.isEmpty()) return
    val colors = OwnTVTheme.colors
    val regieEnde = besetzung.indexOfLast { it.regie }
    // Stabile Schluessel, damit der Fokus nicht springt, wenn die Reihe neu zusammengesetzt wird.
    // Dieselbe Person kann zweimal dastehen (Regie UND Rolle) — doppelte Schluessel wirft die
    // LazyRow hin, deshalb bekommt die Wiederholung ihre Nummer angehaengt.
    val schluessel = remember(besetzung) {
        val zaehler = mutableMapOf<Long, Int>()
        besetzung.map { person ->
            val n = zaehler.merge(person.id, 1, Int::plus) ?: 1
            if (n == 1) person.id.toString() else "${person.id}#$n"
        }
    }
    Column {
        Text(
            stringResource(R.string.g4k_detail_besetzung),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            itemsIndexed(besetzung, key = { i, _ -> schluessel[i] }) { i, person ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PersonKachel(person) { onPerson(person) }
                    if (i == regieEnde && i < besetzung.lastIndex) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(1.dp)
                                .height(PersonFoto)
                                .background(colors.outlineVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonKachel(person: German4kDarsteller, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.width(PersonBreite),
        shape = Ecke,
        focusedScale = 1.05f,
    ) { _ ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(PersonFoto)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val foto = person.foto?.takeIf { it.isNotBlank() }
                if (foto != null) {
                    AsyncImage(
                        model = foto,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Viele Nebenrollen haben kein Foto; Initialen lesen sich besser als eine Luecke.
                    Text(
                        person.name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                person.name,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val unterzeile = if (person.regie) stringResource(R.string.g4k_detail_regie) else person.rolle.takeIf { it.isNotBlank() }
            if (unterzeile != null) {
                Text(
                    unterzeile,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (person.regie) colors.primary else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Andere Schnitte, Sprachen oder Qualitaeten desselben Titels — der Server liefert sie mit. */
@Composable
private fun FassungenReihe(fassungen: List<German4kFassung>, onFassung: (German4kFassung) -> Unit) {
    if (fassungen.isEmpty()) return
    val colors = OwnTVTheme.colors
    Column {
        Text(
            stringResource(R.string.g4k_detail_fassungen),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            // remoteId ist die Stream-Kennung des Servers, also je Fassung eindeutig.
            itemsIndexed(fassungen, key = { _, f -> f.remoteId }) { _, fassung ->
                FocusableSurface(
                    onClick = { onFassung(fassung) },
                    modifier = Modifier.size(width = 180.dp, height = 64.dp),
                    shape = Ecke,
                    unfocusedContainerColor = colors.surfaceContainerHigh,
                    focusedScale = 1.03f,
                ) { _ ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            fassung.label.takeIf { it.isNotBlank() } ?: fassung.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (fassung.kategorie.isNotBlank()) {
                            Text(
                                fassung.kategorie,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
