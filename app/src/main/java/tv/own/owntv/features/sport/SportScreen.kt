package tv.own.owntv.features.sport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.german4k.German4kFeatures
import tv.own.owntv.core.german4k.German4kSpiel
import tv.own.owntv.core.german4k.SPORT_ZONE
import tv.own.owntv.core.german4k.SportChip
import tv.own.owntv.core.nav.NavVisibility
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.mobil.German4kMobilRahmen
import tv.own.owntv.features.settings.ConfirmDialog
import tv.own.owntv.ui.LocalFormfaktor
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.fokusMitWiederholung
import tv.own.owntv.ui.theme.OwnTVTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalConfiguration
import tv.own.owntv.core.german4k.SportFusszeile
import tv.own.owntv.core.german4k.SportMinute
import tv.own.owntv.core.german4k.SportRegale
import tv.own.owntv.core.german4k.SportSprache
import tv.own.owntv.core.german4k.nachTagen
import tv.own.owntv.core.german4k.spaeterStandardOffen
import tv.own.owntv.core.german4k.sportFusszeile
import tv.own.owntv.core.german4k.sportMinute
import java.time.LocalDate
import kotlinx.coroutines.delay

/** Rot für den LIVE-Punkt — bewusst fest, nicht vom Akzent abhängig. */
internal val SportLiveRot = Color(0xFFE53935)

private val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** HH:mm in Berlin — dort gilt der Spielplan. */
internal fun uhrzeitBerlin(zeit: Instant): String = UHRZEIT.format(zeit.atZone(SPORT_ZONE))

/**
 * German4K 3.0: Bereich „Fußball" (Sport-Hub).
 *
 * Fernseher: Kopf mit Stand, Chips, darunter Regale (Jetzt live, Heute, Morgen & später — letzteres
 * zugeklappt). Handy: dieselben Karten untereinander, ohne Vorschau. Ein Klick holt IMMER neu und
 * spielt direkt, wenn genau ein eingeschalteter Sender feststeht; sonst öffnet die Spielseite.
 */
@Composable
fun SportScreen(
    liveVm: LiveViewModel,
    onPlayChannel: (ChannelEntity) -> Unit,
    onOpenLiveTv: (Long?) -> Unit,
    onBack: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean,
    modifier: Modifier = Modifier,
    vm: SportViewModel = koinViewModel(),
) {
    val formfaktor = LocalFormfaktor.current
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    val antwort by vm.antwort.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    val chip by vm.chip.collectAsStateWithLifecycle()
    val regale by vm.regale.collectAsStateWithLifecycle()
    val offenesSpiel by vm.offenesSpiel.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        vm.starte()
        onDispose { vm.stoppe() }
    }

    // Feature auf „entwicklung": einmal den bekannten Hinweis zeigen.
    var devHinweis by rememberSaveable { mutableStateOf(German4kFeatures.inDevelopment(NavVisibility.SPORT_FEATURE)) }
    if (devHinweis) {
        ConfirmDialog(
            title = stringResource(R.string.g4k_feature_dev_title),
            message = stringResource(R.string.g4k_feature_dev_body),
            onConfirm = { devHinweis = false },
            onDismiss = { devHinweis = false; onBack() },
            confirmLabel = R.string.g4k_feature_dev_open,
        )
    }

    val spiel = offenesSpiel
    if (spiel != null) {
        SportSpielScreen(
            spiel = spiel,
            vm = vm,
            liveVm = liveVm,
            onPlayChannel = onPlayChannel,
            onOpenLiveTv = onOpenLiveTv,
            onBack = { vm.schliesse() },
            previewEnabled = previewEnabled,
            modifier = modifier,
        )
        return
    }
    BackHandler(enabled = !formfaktor.mobil) { onBack() }

    val klick: (German4kSpiel) -> Unit = { s ->
        scope.launch {
            when (val k = vm.klick(s)) {
                is SportKlick.Spielen -> onPlayChannel(k.channel)
                is SportKlick.Oeffnen -> vm.oeffne(k.spiel)
            }
        }
    }

    val standText = antwort?.stand?.let { st ->
        runCatching { Instant.parse(st) }.getOrNull()?.let { uhrzeitBerlin(it) }
    }?.let { if (offline) stringResource(R.string.g4k_sport_offline, it) else stringResource(R.string.g4k_sport_stand, it) }

    val ersterFokus = remember { FocusRequester() }
    LaunchedEffect(regale.leer) {
        if (!regale.leer && !formfaktor.mobil) fokusMitWiederholung(ersterFokus)
    }

    val inhalt: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize().padding(horizontal = if (formfaktor.mobil) 12.dp else 24.dp, vertical = if (formfaktor.mobil) 8.dp else 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.g4k_nav_fussball),
                    style = if (formfaktor.mobil) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                standText?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
            }
            Spacer(Modifier.height(10.dp))
            ChipZeile(chip = chip, onChip = { vm.chip.value = it }, onFocused = onChildFocused)
            Spacer(Modifier.height(12.dp))
            val a = antwort
            when {
                a == null -> Hinweis(stringResource(R.string.g4k_sport_laden))
                !a.ok -> Hinweis(a.grund ?: stringResource(R.string.g4k_sport_aus))
                regale.leer -> Hinweis(stringResource(R.string.g4k_sport_leer))
                // German4K 3.0/32: Handy in Abschnitten mit Tagesköpfen statt einer flachen Liste.
                formfaktor.mobil -> MobilListe(regale, onKlick = klick)
                else -> TvRegale(regale = regale, ersterFokus = ersterFokus, onKlick = klick, onFocused = onChildFocused)
            }
        }
    }

    if (formfaktor.mobil) {
        German4kMobilRahmen(
            kategorien = emptyList(),
            selectedIndex = 0,
            onSelect = {},
            kategorieOffen = true,
            onKategorieOffen = {},
            titel = stringResource(R.string.g4k_nav_fussball),
            modifier = modifier,
            inhalt = inhalt,
        )
    } else {
        Box(modifier.fillMaxSize().focusGroup()) { inhalt() }
    }
}

@Composable
private fun Hinweis(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = OwnTVTheme.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun ChipZeile(chip: SportChip, onChip: (SportChip) -> Unit, onFocused: () -> Unit) {
    val colors = OwnTVTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SportChip.entries.forEach { c ->
            val aktiv = c == chip
            FocusableSurface(
                onClick = { onChip(c); onFocused() },
                selected = aktiv,
                shape = RoundedCornerShape(50),
            ) { _ ->
                Text(
                    stringResource(chipLabel(c)),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (aktiv) colors.primary else colors.onSurfaceVariant,
                    fontWeight = if (aktiv) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun chipLabel(c: SportChip): Int = when (c) {
    SportChip.ALLE -> R.string.g4k_sport_chip_alle
    SportChip.BUNDESLIGA -> R.string.g4k_sport_chip_bundesliga
    SportChip.CL -> R.string.g4k_sport_chip_cl
    SportChip.POKAL -> R.string.g4k_sport_chip_pokal
    SportChip.INTERNATIONAL -> R.string.g4k_sport_chip_international
}

// German4K 3.0/32 (S2): Karten je Rasterzeile auf dem Fernseher.
private const val TV_SPALTEN = 4

/** Datum im Tageskopf, z. B. „Do., 01.10." — Wochentag in der Sprache der App. */
private const val TAG_MUSTER = "EEE, dd.MM."

@Composable
private fun TvRegale(
    regale: SportRegale,
    ersterFokus: FocusRequester,
    onKlick: (German4kSpiel) -> Unit,
    onFocused: () -> Unit,
) {
    // German4K 3.0/32 (S2): „Morgen & später" ist offen, wenn heute höchstens acht Spiele laufen.
    var spaeterOffen by rememberSaveable { mutableStateOf(spaeterStandardOffen(regale.heute.size)) }
    val liveRegal = regale.live + regale.beendet
    // Wer bekommt den Startfokus: die erste Karte des ersten sichtbaren Regals.
    val erstesRegal = when {
        liveRegal.isNotEmpty() -> 0
        regale.heute.isNotEmpty() -> 1
        else -> 2
    }
    val tage = remember(regale.spaeter) { nachTagen(regale.spaeter) }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        // „Jetzt live" bleibt vorn und bleibt eine Reihe.
        if (liveRegal.isNotEmpty()) item(key = "live") {
            Regal(stringResource(R.string.g4k_sport_regal_live), liveRegal, if (erstesRegal == 0) ersterFokus else null, onKlick, onFocused)
        }
        // German4K 3.0/32 (S2): „Heute" als Raster mit vier Karten je Zeile.
        if (regale.heute.isNotEmpty()) {
            item(key = "heute") { AbschnittTitel(stringResource(R.string.g4k_sport_regal_heute)) }
            rasterZeilen(regale.heute, if (erstesRegal == 1) ersterFokus else null, onKlick, onFocused)
        }
        if (regale.spaeter.isNotEmpty()) {
            item(key = "spaeter") {
                val offen = spaeterOffen || erstesRegal == 2
                FocusableSurface(
                    onClick = { spaeterOffen = !spaeterOffen },
                    shape = RoundedCornerShape(12.dp),
                    contentAlignment = Alignment.CenterStart,
                    modifier = if (erstesRegal == 2) Modifier.focusRequester(ersterFokus) else Modifier,
                ) { _ ->
                    Text(
                        (if (offen) "▾ " else "▸ ") + stringResource(R.string.g4k_sport_regal_spaeter) + " · " + regale.spaeter.size,
                        style = MaterialTheme.typography.titleMedium,
                        color = OwnTVTheme.colors.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            // German4K 3.0/32 (S2): nach Tagen getrennt statt als ein Block.
            if (spaeterOffen || erstesRegal == 2) tage.forEach { (tag, spiele) ->
                item { TagTitel(tag) }
                rasterZeilen(spiele, null, onKlick, onFocused)
            }
        }
    }
}

/** German4K 3.0/32 (S2): Spiele in Zeilen zu [TV_SPALTEN] Karten; D-Pad läuft über die 2D-Fokussuche. */
private fun LazyListScope.rasterZeilen(
    spiele: List<German4kSpiel>,
    fokus: FocusRequester?,
    onKlick: (German4kSpiel) -> Unit,
    onFocused: () -> Unit,
) {
    val zeilen = spiele.chunked(TV_SPALTEN)
    zeilen.forEachIndexed { zi, zeile ->
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                zeile.forEachIndexed { si, s ->
                    val f = fokus?.takeIf { zi == 0 && si == 0 }
                    SpielKarte(
                        spiel = s,
                        onClick = { onFocused(); onKlick(s) },
                        modifier = Modifier
                            .weight(1f)
                            .height(140.dp)
                            .then(if (f != null) Modifier.focusRequester(f) else Modifier),
                    )
                }
                // Letzte Zeile: gleiche Kartenbreite wie darüber.
                repeat(TV_SPALTEN - zeile.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AbschnittTitel(titel: String) {
    Text(titel, style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurface, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

/** German4K 3.0/32: Tageskopf „Morgen · Do., 01.10." bzw. nur das Datum für spätere Tage. */
@Composable
private fun TagTitel(tag: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val datum = remember(tag, locale) { DateTimeFormatter.ofPattern(TAG_MUSTER, locale).format(tag) }
    val morgen = tag == LocalDate.now(SPORT_ZONE).plusDays(1)
    AbschnittTitel(if (morgen) stringResource(R.string.g4k_sport_tag_morgen, datum) else datum)
}

@Composable
private fun Regal(titel: String, spiele: List<German4kSpiel>, fokus: FocusRequester?, onKlick: (German4kSpiel) -> Unit, onFocused: () -> Unit) {
    Column {
        Text(titel, style = MaterialTheme.typography.titleMedium, color = OwnTVTheme.colors.onSurface, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        KartenReihe(spiele, fokus, onKlick, onFocused)
    }
}

@Composable
private fun KartenReihe(spiele: List<German4kSpiel>, fokus: FocusRequester?, onKlick: (German4kSpiel) -> Unit, onFocused: () -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        items(spiele, key = { it.id }) { s ->
            val f = fokus?.takeIf { s.id == spiele.first().id }
            SpielKarte(
                spiel = s,
                onClick = { onFocused(); onKlick(s) },
                modifier = Modifier
                    .width(280.dp)
                    .height(140.dp)
                    .then(if (f != null) Modifier.focusRequester(f) else Modifier),
            )
        }
    }
}

/** German4K 3.0/32 (S2): Handy einspaltig — Jetzt live, Heute, je Tag ein Kopf, Beendet. */
@Composable
private fun MobilListe(regale: SportRegale, onKlick: (German4kSpiel) -> Unit) {
    val tage = remember(regale.spaeter) { nachTagen(regale.spaeter) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        fun abschnitt(spiele: List<German4kSpiel>, kopf: @Composable () -> Unit) {
            if (spiele.isEmpty()) return
            item { kopf() }
            items(spiele, key = { it.id }) { s ->
                SpielKarte(spiel = s, onClick = { onKlick(s) }, modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        }
        abschnitt(regale.live) { AbschnittTitel(stringResource(R.string.g4k_sport_regal_live)) }
        abschnitt(regale.heute) { AbschnittTitel(stringResource(R.string.g4k_sport_regal_heute)) }
        tage.forEach { (tag, spiele) -> abschnitt(spiele) { TagTitel(tag) } }
        abschnitt(regale.beendet) { AbschnittTitel(stringResource(R.string.g4k_sport_regal_beendet)) }
    }
}

/** Eine Spielkarte: Wettbewerb, Paarung, Anstoß oder LIVE mit Minute, Sprache + Marke des ersten Senders. */
@Composable
internal fun SpielKarte(spiel: German4kSpiel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        unfocusedContainerColor = colors.surfaceContainer,
        contentAlignment = Alignment.TopStart,
        modifier = modifier.alpha(if (spiel.beendet) 0.5f else 1f),
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // German4K 3.0/32: Wettbewerb bekommt die ganze Restbreite (vorher teilte er sie mit einem
                // zweiten Gewichts-Spacer und wurde zu „Nations …"), die Uhrzeit bleibt rechtsbündig.
                // Passt der Name nicht, erst auf 11sp verkleinern, dann kürzen.
                val wettbewerb = spiel.wettbewerb
                if (wettbewerb != null) {
                    var klein by remember(wettbewerb) { mutableStateOf(false) }
                    val basis = MaterialTheme.typography.labelMedium
                    Box(Modifier.weight(1f)) {
                        Text(
                            wettbewerb,
                            style = if (klein) basis.copy(fontSize = 11.sp) else basis,
                            color = colors.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { r -> if (!klein && r.hasVisualOverflow) klein = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(colors.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(6.dp))
                if (spiel.laeuft) {
                    // German4K 3.0/32 (R2): roter Chip „LIVE 34′" statt der großen Uhrzeit.
                    LiveMinutenChip(spiel)
                } else {
                    Text(uhrzeitBerlin(spiel.start), style = MaterialTheme.typography.titleLarge, color = colors.onSurface, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                spiel.titel,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                fusszeileText(spiel),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** German4K 3.0/32 (R2): LIVE-Chip mit grober Spielminute, zählt alle 30 s weiter. */
@Composable
private fun LiveMinutenChip(spiel: German4kSpiel) {
    val jetzt by produceState(Instant.now(), spiel.start) {
        while (true) {
            value = Instant.now()
            delay(30_000)
        }
    }
    val minute = when (val m = sportMinute(spiel.start, jetzt)) {
        is SportMinute.Minute -> stringResource(R.string.g4k_sport_minute, m.n)
        SportMinute.Halbzeit -> stringResource(R.string.g4k_sport_halbzeit)
        SportMinute.Nachspielzeit -> stringResource(R.string.g4k_sport_nachspielzeit)
    }
    Text(
        stringResource(R.string.g4k_sport_live_minute, minute),
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SportLiveRot)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** German4K 3.0/32: Wort zur Sprache einer Senderflagge. */
internal fun spracheText(s: SportSprache): Int = when (s) {
    SportSprache.DEUTSCH -> R.string.g4k_sport_sprache_de
    SportSprache.ENGLISCH -> R.string.g4k_sport_sprache_en
    SportSprache.SPANISCH -> R.string.g4k_sport_sprache_es
    SportSprache.FRANZOESISCH -> R.string.g4k_sport_sprache_fr
    SportSprache.ITALIENISCH -> R.string.g4k_sport_sprache_it
    SportSprache.TUERKISCH -> R.string.g4k_sport_sprache_tr
}

/**
 * German4K 3.0/32 (R2): Fußzeile der Karte — „🇩🇪 Deutsch auf DAZN · 10 weitere" statt des
 * technischen Platznamens. Kaskade Sender → Rechte („Bei …") → Bereich („Im Bereich …").
 */
@Composable
internal fun fusszeileText(spiel: German4kSpiel): String = when (val f = sportFusszeile(spiel)) {
    is SportFusszeile.Sender -> {
        val kern = f.sprache?.let { stringResource(R.string.g4k_sport_fuss_sprache_marke, stringResource(spracheText(it)), f.marke) } ?: f.marke
        val vorne = listOfNotNull(f.flagge, kern).joinToString(" ")
        if (f.weitere > 0) vorne + " · " + stringResource(R.string.g4k_sport_fuss_weitere, f.weitere) else vorne
    }
    is SportFusszeile.Rechte -> stringResource(R.string.g4k_sport_fuss_rechte, f.sender)
    is SportFusszeile.Bereich -> stringResource(R.string.g4k_sport_fuss_bereich, f.name)
    SportFusszeile.Keine -> ""
}
