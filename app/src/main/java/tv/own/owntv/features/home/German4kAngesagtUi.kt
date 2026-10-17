package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import tv.own.owntv.core.german4k.AngesagtBadge
import tv.own.owntv.core.german4k.German4kAngesagtKarte
import tv.own.owntv.core.german4k.German4kDetails
import tv.own.owntv.core.german4k.German4kTitel
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.ui.LocalFormfaktor
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.german4kNote
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

// German4K 3.2 (N1/N2): Angesagt-Hero, das gemischte Regal „🔥 Angesagt" und die Top 10 mit großen
// Umriss-Zahlen. Aussehen nach den Entwürfen docs/app-rebrand/ultra-3.2/entwurf-1…3.jpg.

/** Lila des Entwurfs (Kennung FILM, Umriss der Rangzahlen). */
internal val G4kLila = Color(0xFFA274FB)
/** Hellblau des Entwurfs (Kennung SERIE). */
internal val G4kHellblau = Color(0xFF38BDF8)
private val G4kDunkel = Color(0xFF0B0B10)

/**
 * Startseite: Hero = #1 Angesagt, darunter EIN gemischtes Regal „🔥 Angesagt". Keine zwei Top-10-
 * Regale hier (zu voll, Rückmeldung Steven 18.09.).
 */
@Composable
internal fun German4kAngesagtStart(
    karten: List<German4kAngesagtKarte>,
    details: German4kDetails?,
    favorit: Boolean,
    onAbspielen: (German4kAngesagtKarte) -> Unit,
    onTrailer: ((String) -> Unit),
    onFavorit: (German4kAngesagtKarte) -> Unit,
    onKarte: (German4kAngesagtKarte) -> Unit,
    onFocus: () -> Unit,
    abspielenFokus: FocusRequester?,
    trailerFokus: FocusRequester?,
) {
    val hero = karten.firstOrNull() ?: return
    // German4K 3.2: Notschalter `angesagt_hero` aus → nur das Regal; der Startfokus geht dann auf dessen erste Karte.
    val mitHero = tv.own.owntv.features.german4kAn(tv.own.owntv.core.german4k.German4kSchalter.ANGESAGT_HERO)
    Column(Modifier.fillMaxWidth()) {
        if (mitHero) {
            AngesagtHero(hero, details, favorit, onAbspielen, onTrailer, onFavorit, onFocus, abspielenFokus, trailerFokus)
            Spacer(Modifier.height(12.dp))
        }
        German4kAngesagtRegal(
            titel = tv.own.owntv.features.german4kText("angesagt_titel", stringResource(R.string.g4k_angesagt_titel)),
            untertitel = tv.own.owntv.features.german4kText("angesagt_untertitel", stringResource(R.string.g4k_angesagt_untertitel)),
            karten = karten,
            mitKennung = true,
            onKarte = onKarte,
            onFocus = onFocus,
            ersteFokus = if (mitHero) null else abspielenFokus,
        )
    }
}

@Composable
private fun AngesagtHero(
    k: German4kAngesagtKarte,
    details: German4kDetails?,
    favorit: Boolean,
    onAbspielen: (German4kAngesagtKarte) -> Unit,
    onTrailer: (String) -> Unit,
    onFavorit: (German4kAngesagtKarte) -> Unit,
    onFocus: () -> Unit,
    abspielenFokus: FocusRequester?,
    trailerFokus: FocusRequester?,
) {
    val colors = OwnTVTheme.colors
    val mobil = LocalFormfaktor.current.mobil
    val bild = details?.backdrop ?: k.querbild ?: k.plakat
    val handlung = details?.plot?.takeIf { it.isNotBlank() } ?: k.handlung
    val imdb = details?.noten?.imdb ?: k.note
    val sep = stringResource(R.string.g4k_trenner)
    val meta = listOfNotNull(
        stringResource(if (k.serie) R.string.g4k_art_serie else R.string.g4k_art_film),
        (k.jahr ?: details?.jahr?.toIntOrNull())?.toString(),
        imdb?.let { stringResource(R.string.g4k_angesagt_imdb, german4kNote(it)) },
    ).joinToString(sep)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (mobil) 240.dp else 210.dp)
            .padding(horizontal = Dimens.HomeRowPaddingH)
            .clip(RoundedCornerShape(18.dp))
            .background(G4kDunkel),
    ) {
        if (!bild.isNullOrBlank()) {
            // Bild rechts, nach links in den Hintergrund verlaufend — der Text links bleibt lesbar.
            AsyncImage(
                model = bild,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(if (mobil) 1f else 0.62f),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to G4kDunkel, if (mobil) 0.15f to G4kDunkel.copy(alpha = 0.85f) else 0.38f to G4kDunkel,
                        0.75f to G4kDunkel.copy(alpha = 0.25f), 1f to Color.Transparent,
                    ),
                ),
            )
        }
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(if (mobil) 0.95f else 0.55f)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.g4k_angesagt_nr1),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.primary).padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                German4kTitel.titel(k.name),
                style = if (mobil) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(meta, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
            if (!handlung.isNullOrBlank()) {
                Text(handlung, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                OwnTVButton(
                    label = stringResource(R.string.g4k_detail_abspielen),
                    onClick = { onAbspielen(k) },
                    icon = OwnTVIcon.PLAY,
                    modifier = Modifier
                        .then(if (abspielenFokus != null) Modifier.focusRequester(abspielenFokus) else Modifier)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
                details?.trailer?.let { key ->
                    OwnTVButton(
                        label = stringResource(R.string.g4k_detail_trailer),
                        onClick = { onTrailer(key) },
                        icon = OwnTVIcon.VIDEO,
                        style = OwnTVButtonStyle.SECONDARY,
                        modifier = Modifier.then(if (trailerFokus != null) Modifier.focusRequester(trailerFokus) else Modifier),
                    )
                }
                OwnTVButton(
                    label = stringResource(if (favorit) R.string.g4k_detail_favorit_entfernen else R.string.g4k_detail_favorit),
                    onClick = { onFavorit(k) },
                    icon = OwnTVIcon.FAVORITE,
                    style = OwnTVButtonStyle.SECONDARY,
                )
            }
        }
    }
}

/** Reihenkopf: Titel groß, Untertitel dezent daneben (Entwurf). */
@Composable
private fun RegalKopf(titel: String, untertitel: String?) {
    val colors = OwnTVTheme.colors
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(start = Dimens.HomeRowPaddingH, end = Dimens.HomeRowPaddingH),
    ) {
        Text(titel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1)
        if (!untertitel.isNullOrBlank() && !LocalFormfaktor.current.kompakt) {
            Spacer(Modifier.width(12.dp))
            Text(untertitel, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(10.dp))
}

/**
 * Ein Poster-Regal. [mitKennung] = gemischtes Regal: unten auf jedem Poster dezent „● FILM" (lila) bzw.
 * „● SERIE" (hellblau) auf dunklem Verlauf. Unter dem Regal steht, was gerade den Fokus hat (Titel + Note) —
 * die Poster selbst tragen keinen Titel, wie im Entwurf.
 */
@Composable
internal fun German4kAngesagtRegal(
    titel: String,
    untertitel: String?,
    karten: List<German4kAngesagtKarte>,
    mitKennung: Boolean,
    onKarte: (German4kAngesagtKarte) -> Unit,
    onFocus: () -> Unit,
    ersteFokus: FocusRequester?,
    seitenrand: Dp = Dimens.HomeRowPaddingH,
) {
    if (karten.isEmpty()) return
    val mobil = LocalFormfaktor.current.mobil
    var fokus by remember(karten) { mutableStateOf<German4kAngesagtKarte?>(null) }
    Column(Modifier.fillMaxWidth()) {
        RegalKopf(titel, untertitel)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = seitenrand, vertical = 6.dp),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(karten, key = { _, k -> "${k.serie}:${k.tmdb}" }) { i, k ->
                AngesagtPoster(
                    k = k,
                    breite = if (mobil) 104.dp else 104.dp,
                    mitKennung = mitKennung,
                    onClick = { onKarte(k) },
                    modifier = Modifier
                        .then(if (i == 0 && ersteFokus != null) Modifier.focusRequester(ersteFokus) else Modifier)
                        .onFocusChanged { if (it.hasFocus) { fokus = k; onFocus() } },
                )
            }
        }
        FokusHinweis(fokus)
    }
}

/**
 * „🏆 Top 10 … bei German4K": große Umriss-Zahl links, das Poster rechts darüber (Entwurf 2/3).
 * Keine Kundenzahlen, nur der Platz.
 */
@Composable
internal fun German4kTop10Regal(
    titel: String,
    karten: List<German4kAngesagtKarte>,
    onKarte: (German4kAngesagtKarte) -> Unit,
    onFocus: () -> Unit,
    ersteFokus: FocusRequester?,
    seitenrand: Dp = Dimens.HomeRowPaddingH,
) {
    if (karten.isEmpty()) return
    val mobil = LocalFormfaktor.current.mobil
    val posterBreite = if (mobil) 88.dp else 112.dp
    val zahlGroesse: TextUnit = if (mobil) 88.sp else 130.sp
    var fokus by remember(karten) { mutableStateOf<German4kAngesagtKarte?>(null) }
    Column(Modifier.fillMaxWidth()) {
        RegalKopf(titel, tv.own.owntv.features.german4kText("top10_untertitel", stringResource(R.string.g4k_top10_untertitel)))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (mobil) 4.dp else 8.dp),
            contentPadding = PaddingValues(horizontal = seitenrand, vertical = 6.dp),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(karten, key = { _, k -> "${k.serie}:${k.tmdb}" }) { i, k ->
                val zahlBreite = (if (k.platz >= 10) 1.25f else 0.7f) * (if (mobil) 64f else 100f)
                Box(Modifier.width(zahlBreite.dp + posterBreite)) {
                    Text(
                        k.platz.toString(),
                        style = TextStyle(
                            fontSize = zahlGroesse,
                            fontWeight = FontWeight.Black,
                            color = G4kLila,
                            drawStyle = Stroke(width = if (mobil) 4f else 6f),
                        ),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.align(Alignment.BottomStart).offset(y = if (mobil) 18.dp else 30.dp),
                    )
                    AngesagtPoster(
                        k = k,
                        breite = posterBreite,
                        mitKennung = false,
                        onClick = { onKarte(k) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .then(if (i == 0 && ersteFokus != null) Modifier.focusRequester(ersteFokus) else Modifier)
                            .onFocusChanged { if (it.hasFocus) { fokus = k; onFocus() } },
                    )
                }
            }
        }
        FokusHinweis(fokus)
    }
}

/**
 * Kopf über dem Filme- bzw. Serien-Raster: ganz oben Top 10, darunter „🔥 Angesagt" (ohne die Titel
 * der Top 10 — das entdoppelt schon der Reader). Ein leeres Regal fällt einfach weg.
 */
@Composable
internal fun German4kAngesagtKopf(
    serien: Boolean,
    top: List<German4kAngesagtKarte>,
    angesagt: List<German4kAngesagtKarte>,
    onKarte: (German4kAngesagtKarte, top10: Boolean) -> Unit,
) {
    // German4K 3.2: Notschalter `top10` aus → keine Top-10-Zeile, Angesagt bleibt.
    val top = if (tv.own.owntv.features.german4kAn(tv.own.owntv.core.german4k.German4kSchalter.TOP10)) top else emptyList()
    if (top.isEmpty() && angesagt.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        German4kTop10Regal(
            titel = if (serien) tv.own.owntv.features.german4kText("top10_serien_titel", stringResource(R.string.g4k_top10_serien))
                else tv.own.owntv.features.german4kText("top10_filme_titel", stringResource(R.string.g4k_top10_filme)),
            karten = top,
            onKarte = { onKarte(it, true) },
            onFocus = {},
            ersteFokus = null,
            seitenrand = 0.dp,
        )
        German4kAngesagtRegal(
            titel = tv.own.owntv.features.german4kText("angesagt_titel", stringResource(R.string.g4k_angesagt_titel)),
            untertitel = stringResource(R.string.g4k_angesagt_unter_top),
            karten = angesagt,
            mitKennung = false,
            onKarte = { onKarte(it, false) },
            onFocus = {},
            ersteFokus = null,
            seitenrand = 0.dp,
        )
    }
}

@Composable
private fun FokusHinweis(k: German4kAngesagtKarte?) {
    val colors = OwnTVTheme.colors
    val sep = stringResource(R.string.g4k_trenner)
    val text = k?.let { listOfNotNull(German4kTitel.titel(it.name), it.jahr?.toString(), it.note?.let { n -> stringResource(R.string.g4k_angesagt_imdb, german4kNote(n)) }).joinToString(sep) }
    Text(
        text.orEmpty(),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = Dimens.HomeRowPaddingH, top = 4.dp).height(20.dp),
    )
}

@Composable
private fun AngesagtPoster(
    k: German4kAngesagtKarte,
    breite: Dp,
    mitKennung: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val titel = German4kTitel.titel(k.name)
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.width(breite).semantics { contentDescription = titel },
        shape = RoundedCornerShape(10.dp),
        surface = GlassSurface.CARDS,
        focusedScale = 1.06f,
        glowElevation = 8,
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
    ) { _ ->
        Box(
            Modifier
                .width(breite)
                .height(breite * 1.5f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceContainerLowest),
        ) {
            if (!k.plakat.isNullOrBlank()) {
                AsyncImage(model = k.plakat, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    titel,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
            }
            // German4K 3.2: IMDb-Note oben rechts wie in jeder anderen Reihe (Note aus dem lokalen Katalog).
            tv.own.owntv.ui.components.German4kImdbSchild(k.note, Modifier.align(Alignment.TopEnd))
            k.badge?.let { b ->
                Text(
                    stringResource(
                        when (b) {
                            AngesagtBadge.NEUE_FOLGE -> R.string.g4k_badge_neue_folge
                            AngesagtBadge.NEU -> R.string.g4k_badge_neu
                            AngesagtBadge.VIERK -> R.string.g4k_badge_4k
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (mitKennung) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp),
                ) {
                    val farbe = if (k.serie) G4kHellblau else G4kLila
                    Box(Modifier.size(6.dp).clip(CircleShape).background(farbe))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(if (k.serie) R.string.g4k_kennung_serie else R.string.g4k_kennung_film),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
