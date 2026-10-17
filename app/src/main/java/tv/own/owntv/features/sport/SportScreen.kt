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

/**
 * German4K 3.2 (N7): „⏰ Erinnern“ für die Karten, ohne jede Zwischen-Composable umzubauen. [schluessel] =
 * gemerkte Spiele/Konferenzen, [umschalten] merkt bzw. vergisst (OK lange drücken auf einer Karte).
 */
internal class SportErinnern(val schluessel: Set<String>, val umschalten: (German4kSpiel) -> Unit) {
    fun erinnert(spiel: German4kSpiel): Boolean =
        tv.own.owntv.core.german4k.German4kErinnern.spielSchluessel(spiel.id) in schluessel
}

internal val LocalSportErinnern = androidx.compose.runtime.staticCompositionLocalOf { SportErinnern(emptySet()) {} }

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
    onPlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit,
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
    // German4K 3.2 (N7): gemerkte Spiele + Rückmeldung als kleiner Hinweis unten.
    val erinnertSchluessel by vm.erinnert.collectAsStateWithLifecycle()
    val toast = tv.own.owntv.ui.components.rememberInAppToast()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sportErinnern = remember(erinnertSchluessel) {
        SportErinnern(erinnertSchluessel) { s ->
            scope.launch {
                val r = vm.erinnernUmschalten(s)
                toast.show(
                    when (r) {
                        true -> ctx.getString(R.string.g4k_erinnern_gesetzt, tv.own.owntv.core.german4k.German4kErinnern.VORLAUF_SPIEL_MIN)
                        false -> ctx.getString(R.string.g4k_erinnern_geloescht)
                        null -> ctx.getString(R.string.g4k_erinnern_geht_nicht)
                    },
                )
            }
        }
    }

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
        androidx.compose.runtime.CompositionLocalProvider(LocalSportErinnern provides sportErinnern) {
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
        }
        return
    }
    BackHandler(enabled = !formfaktor.mobil) { onBack() }

    val klick: (German4kSpiel) -> Unit = { s ->
        if (s.istKonferenz) vm.konferenzKlick(s)
        scope.launch {
            when (val k = vm.klick(s)) {
                is SportKlick.Spielen -> onPlayChannel(k.channel, emptyList())
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
            // German4K 3.2 (N7): wie man sich an ein Spiel erinnern lässt — auf dem Fernseher gibt es keinen
            // zweiten Knopf auf der Karte, also lange OK.
            if (!formfaktor.mobil) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.g4k_erinnern_lang_druecken), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
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

    val mitErinnern: @Composable () -> Unit = {
        androidx.compose.runtime.CompositionLocalProvider(LocalSportErinnern provides sportErinnern) { inhalt() }
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
            inhalt = mitErinnern,
        )
        tv.own.owntv.ui.components.InAppToast(toast)
    } else {
        Box(modifier.fillMaxSize().focusGroup()) {
            mitErinnern()
            tv.own.owntv.ui.components.InAppToast(toast)
        }
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

// German4K 3.2 (C5): mit großen Wappen werden die Karten höher (vorher TV 140 dp, Handy 120 dp).
private val TV_KARTE_HOEHE = 204.dp
private val MOBIL_KARTE_HOEHE = 180.dp
private val TV_KONFERENZ_HOEHE = 156.dp
private val MOBIL_KONFERENZ_HOEHE = 150.dp

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
    // German4K 3.2 (N4): die Konferenz-Karte steht ganz oben und bekommt ihn zuerst (-1).
    val erstesRegal = when {
        regale.konferenzen.isNotEmpty() -> -1
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
        // German4K 3.2 (N4): Konferenz-Karten ganz oben, die Einzelspiele bleiben darunter wie bisher.
        regale.konferenzen.forEachIndexed { i, k ->
            item(key = "konferenz-${k.id}") {
                val erinnern = LocalSportErinnern.current
                Row(Modifier.fillMaxWidth().focusGroup(), verticalAlignment = Alignment.CenterVertically) {
                    KonferenzKarte(
                        spiel = k,
                        onClick = { onFocused(); onKlick(k) },
                        modifier = Modifier.weight(1f).height(TV_KONFERENZ_HOEHE)
                            .then(if (i == 0 && erstesRegal == -1) Modifier.focusRequester(ersterFokus) else Modifier),
                    )
                    // German4K 3.2 (N7): eigener Knopf neben der Karte — rechts vom Fokus erreichbar.
                    if (!k.laeuft) {
                        Spacer(Modifier.width(12.dp))
                        tv.own.owntv.ui.components.OwnTVButton(
                            stringResource(if (erinnern.erinnert(k)) R.string.g4k_erinnern_knopf_gesetzt else R.string.g4k_erinnern_knopf),
                            onClick = { erinnern.umschalten(k) },
                            style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
                            selected = erinnern.erinnert(k),
                        )
                    }
                }
            }
        }
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
                            .height(TV_KARTE_HOEHE)
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
                    .height(TV_KARTE_HOEHE)
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
                SpielKarte(spiel = s, onClick = { onKlick(s) }, modifier = Modifier.fillMaxWidth().height(MOBIL_KARTE_HOEHE))
            }
        }
        // German4K 3.2 (N4): Konferenzen zuerst.
        items(regale.konferenzen, key = { "konferenz-${it.id}" }) { k ->
            val erinnern = LocalSportErinnern.current
            Column {
                KonferenzKarte(spiel = k, onClick = { onKlick(k) }, modifier = Modifier.fillMaxWidth().height(MOBIL_KONFERENZ_HOEHE))
                if (!k.laeuft) {
                    Spacer(Modifier.height(6.dp))
                    tv.own.owntv.ui.components.OwnTVButton(
                        stringResource(if (erinnern.erinnert(k)) R.string.g4k_erinnern_knopf_gesetzt else R.string.g4k_erinnern_knopf),
                        onClick = { erinnern.umschalten(k) },
                        style = tv.own.owntv.ui.components.OwnTVButtonStyle.SECONDARY,
                        selected = erinnern.erinnert(k),
                        compact = true,
                    )
                }
            }
        }
        abschnitt(regale.live) { AbschnittTitel(stringResource(R.string.g4k_sport_regal_live)) }
        abschnitt(regale.heute) { AbschnittTitel(stringResource(R.string.g4k_sport_regal_heute)) }
        tage.forEach { (tag, spiele) -> abschnitt(spiele) { TagTitel(tag) } }
        abschnitt(regale.beendet) { AbschnittTitel(stringResource(R.string.g4k_sport_regal_beendet)) }
    }
}

/** German4K 3.2 (C5): wie groß die Wappen auf einer [SpielKarte] stehen. */
internal enum class KartenWappen {
    /** Sport-Hub: Wappen über dem Namen, wie im Entwurf. */
    GROSS,
    /** Startseite „Jetzt im Fußball“: zwei Zeilen „Wappen Name“, Kachel bleibt so hoch wie bisher. */
    KLEIN,
}

/** Eine Spielkarte: Wettbewerb, Paarung, Anstoß oder LIVE mit Minute, Sprache + Marke des ersten Senders. */
@Composable
internal fun SpielKarte(
    spiel: German4kSpiel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wappen: KartenWappen = KartenWappen.GROSS,
) {
    val colors = OwnTVTheme.colors
    val mobil = LocalFormfaktor.current.mobil
    val erinnern = LocalSportErinnern.current
    FocusableSurface(
        onClick = onClick,
        // German4K 3.2 (N7): lange OK = „⏰ Erinnern“ (nur künftige Spiele; sonst sagt der Hinweis, warum nicht).
        onLongClick = { erinnern.umschalten(spiel) },
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
                // German4K 3.2 (C6): Wettbewerbslogo klein vor dem Chip; ohne Logo nichts (kein Platzhalter).
                WettbewerbsLogo(spiel.wettbewerbLogo, if (mobil) WETTBEWERB_LOGO_MOBIL else WETTBEWERB_LOGO_TV)
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
                    Text(
                        (if (erinnern.erinnert(spiel)) "⏰ " else "") + uhrzeitBerlin(spiel.start),
                        style = MaterialTheme.typography.titleLarge, color = colors.onSurface, fontWeight = FontWeight.Bold,
                    )
                }
            }
            // German4K 3.2 (C5): Vereinswappen statt der reinen Textpaarung; ohne Heim/Gast wie bisher der Titel.
            when {
                !spiel.hatPaarung() -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        spiel.titel,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                wappen == KartenWappen.GROSS -> {
                    Spacer(Modifier.height(8.dp))
                    PaarungMitWappen(
                        spiel = spiel,
                        wappen = if (mobil) WAPPEN_MOBIL else WAPPEN_TV,
                        namenStil = if (mobil) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    )
                }
                else -> {
                    Spacer(Modifier.height(8.dp))
                    PaarungKompakt(spiel, wappen = if (mobil) WAPPEN_KLEIN_MOBIL else WAPPEN_KLEIN_TV)
                }
            }
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

// German4K 3.2 (C5): Wappengrößen — Hub-Karte groß, Startseite und Konferenz klein.
private val WAPPEN_TV = 76.dp
private val WAPPEN_MOBIL = 56.dp
private val WAPPEN_KLEIN_TV = 26.dp
private val WAPPEN_KLEIN_MOBIL = 24.dp
private val WAPPEN_KONFERENZ = 26.dp

// German4K 3.2 (C6): Wettbewerbslogo dezent — kleiner als die Wappen, etwa so hoch wie der Chip bzw. Kopftext.
private val WETTBEWERB_LOGO_TV = 22.dp
private val WETTBEWERB_LOGO_MOBIL = 18.dp
private val WETTBEWERB_LOGO_KONFERENZ_TV = 24.dp

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

/** „Sa 15:30“ für die Konferenz-Karte, Wochentag in der Sprache der App. */
private const val KONFERENZ_ZEIT_MUSTER = "EEE HH:mm"

/**
 * German4K 3.2 (N4): die Konferenz-Karte — „🔴 Bundesliga-Konferenz · Sa 15:30 · 5 Spiele“, darunter die
 * Paarungen klein, LIVE ab Anstoß −10 min (Server-Zustand). Klick spielt wie ein Spiel (frisch geholte
 * Platzwahl, nie gespeicherte IDs), lange OK = ⏰ Erinnern.
 */
@Composable
internal fun KonferenzKarte(spiel: German4kSpiel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val erinnern = LocalSportErinnern.current
    val mobil = LocalFormfaktor.current.mobil
    val locale = LocalConfiguration.current.locales[0]
    val zeit = remember(spiel.start, locale) { DateTimeFormatter.ofPattern(KONFERENZ_ZEIT_MUSTER, locale).format(spiel.start.atZone(SPORT_ZONE)) }
    FocusableSurface(
        onClick = onClick,
        onLongClick = { erinnern.umschalten(spiel) },
        shape = RoundedCornerShape(16.dp),
        unfocusedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.TopStart,
        modifier = modifier,
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // German4K 3.2 (C6): mit Logo steht das Logo statt des roten Punkts vor dem Titel; ohne Logo
                // bleibt der Kopf wie in 3.2 vorher. Der rote LIVE-Chip rechts bleibt in beiden Fällen.
                val logo = spiel.wettbewerbLogo
                WettbewerbsLogo(logo, if (mobil) WETTBEWERB_LOGO_MOBIL else WETTBEWERB_LOGO_KONFERENZ_TV, abstand = 8.dp)
                Text(
                    stringResource(if (logo != null) R.string.g4k_sport_konferenz_kopf_logo else R.string.g4k_sport_konferenz_kopf, spiel.titel, zeit, spiel.gast),
                    style = MaterialTheme.typography.titleLarge, color = colors.onSurface, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (erinnern.erinnert(spiel)) {
                    Spacer(Modifier.width(8.dp))
                    Text("⏰", style = MaterialTheme.typography.titleLarge)
                }
                if (spiel.laeuft) {
                    Spacer(Modifier.width(8.dp))
                    LiveMinutenChip(spiel)
                }
            }
            if (spiel.paarungen.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // German4K 3.2 (C5): kleine Wappen vor jedem Verein statt einer Textzeile „A – B · C – D“.
                KonferenzPaarungen(spiel.paarungen, wappen = WAPPEN_KONFERENZ, zeilen = 2)
            }
            Spacer(Modifier.weight(1f))
            val fuss = fusszeileText(spiel)
            Text(
                if (spiel.bestaetigt == false) listOf(fuss, stringResource(R.string.g4k_sport_konferenz_laut_plan)).filter { it.isNotBlank() }.joinToString(" · ") else fuss,
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
