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
                formfaktor.mobil -> MobilListe(regale.live + regale.heute + regale.spaeter + regale.beendet, onKlick = klick)
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

@Composable
private fun TvRegale(
    regale: tv.own.owntv.core.german4k.SportRegale,
    ersterFokus: FocusRequester,
    onKlick: (German4kSpiel) -> Unit,
    onFocused: () -> Unit,
) {
    var spaeterOffen by rememberSaveable { mutableStateOf(false) }
    val liveRegal = regale.live + regale.beendet
    // Wer bekommt den Startfokus: die erste Karte des ersten sichtbaren Regals.
    val erstesRegal = when {
        liveRegal.isNotEmpty() -> 0
        regale.heute.isNotEmpty() -> 1
        else -> 2
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (liveRegal.isNotEmpty()) item(key = "live") {
            Regal(stringResource(R.string.g4k_sport_regal_live), liveRegal, if (erstesRegal == 0) ersterFokus else null, onKlick, onFocused)
        }
        if (regale.heute.isNotEmpty()) item(key = "heute") {
            Regal(stringResource(R.string.g4k_sport_regal_heute), regale.heute, if (erstesRegal == 1) ersterFokus else null, onKlick, onFocused)
        }
        if (regale.spaeter.isNotEmpty()) item(key = "spaeter") {
            val offen = spaeterOffen || erstesRegal == 2
            Column {
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
                if (offen) {
                    Spacer(Modifier.height(8.dp))
                    KartenReihe(regale.spaeter, null, onKlick, onFocused)
                }
            }
        }
    }
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

@Composable
private fun MobilListe(spiele: List<German4kSpiel>, onKlick: (German4kSpiel) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(spiele, key = { it.id }) { s ->
            SpielKarte(spiel = s, onClick = { onKlick(s) }, modifier = Modifier.fillMaxWidth().height(120.dp))
        }
    }
}

/** Eine Spielkarte: Wettbewerb, Paarung, Anstoß oder LIVE, erste Senderzeile. */
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
                spiel.wettbewerb?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(7.dp))
                            .background(colors.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (spiel.laeuft) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(SportLiveRot))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.g4k_sport_live), style = MaterialTheme.typography.labelMedium, color = SportLiveRot, fontWeight = FontWeight.Bold)
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
                senderZeile(spiel),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Erste Senderzeile nach der Kaskade: Sender → Rechte → Bereich. */
@Composable
internal fun senderZeile(spiel: German4kSpiel): String {
    val erster = spiel.sender.firstOrNull()
    return when {
        erster != null && spiel.sender.size > 1 -> erster.name + " · " + stringResource(R.string.g4k_sport_sender_mehr, spiel.sender.size)
        erster != null -> erster.name
        spiel.rechte != null -> stringResource(R.string.g4k_sport_marke, spiel.rechte!!.sender)
        spiel.bereich != null -> stringResource(R.string.g4k_sport_bereich, spiel.bereich!!.name)
        else -> ""
    }
}
