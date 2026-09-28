package tv.own.owntv.features.sport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.german4k.German4kSpiel
import tv.own.owntv.core.german4k.German4kSportSender
import tv.own.owntv.features.live.LiveViewModel
import tv.own.owntv.features.mobil.German4kMobilKopfzeile
import tv.own.owntv.player.ExoPreviewSurface
import tv.own.owntv.ui.LocalFormfaktor
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.fokusMitWiederholung
import tv.own.owntv.ui.theme.OwnTVTheme

/** Eine Zeile der Spielseite — was sie zeigt und was ein Klick tut. */
private sealed interface SpielZeile {
    data class Sender(val sender: German4kSportSender) : SpielZeile
    /** Keine Sender bekannt: Marke mit Kategorie zum Öffnen. */
    data class Marke(val name: String, val kategorie: String?) : SpielZeile
    /** Keine Sender, keine Marke: der Bereich, in dem es laufen wird. */
    data class Bereich(val name: String) : SpielZeile
}

/** Kaskade (verbindlich): Sender → Rechte → Bereich. Keine fünfte Stufe. */
private fun zeilenFuer(spiel: German4kSpiel): List<SpielZeile> = when {
    spiel.sender.isNotEmpty() -> spiel.sender.map { SpielZeile.Sender(it) }
    spiel.rechte != null -> listOf(SpielZeile.Marke(spiel.rechte!!.sender, spiel.rechte!!.kategorie))
    spiel.bereich != null -> listOf(SpielZeile.Bereich(spiel.bereich!!.name))
    else -> emptyList()
}

/**
 * German4K 3.0: die Seite eines Spiels. Fernseher: links Kopf + Senderzeilen, rechts die Vorschau
 * (wie Live TV, 700 ms nach dem Fokus). Handy: dieselben Zeilen ohne Vorschau.
 *
 * Abspielen holt immer neu ([SportViewModel.kanalFuer]) — die Vorschau darf aus dem Stand der
 * letzten Minute auflösen, der Vollbildstart nie.
 */
@Composable
internal fun SportSpielScreen(
    spiel: German4kSpiel,
    vm: SportViewModel,
    liveVm: LiveViewModel,
    onPlayChannel: (ChannelEntity) -> Unit,
    onOpenLiveTv: (Long?) -> Unit,
    onBack: () -> Unit,
    previewEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val formfaktor = LocalFormfaktor.current
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    val zeilen = remember(spiel) { zeilenFuer(spiel) }
    val mitVorschau = !formfaktor.mobil && previewEnabled

    BackHandler { onBack() }
    DisposableEffect(Unit) { onDispose { liveVm.stopPreview() } }

    // Welche Zeile hat den Fokus, und welcher Kanal gehört dazu (null = nicht in der eigenen Liste).
    var fokusIndex by remember { mutableStateOf(-1) }
    var vorschauKanal by remember { mutableStateOf<ChannelEntity?>(null) }
    // Aufgelöste Kanäle je Sendername — nur für die Anzeige (grau, wenn nicht in der Liste).
    var aufgeloest by remember(spiel) { mutableStateOf<Map<String, ChannelEntity?>>(emptyMap()) }
    LaunchedEffect(spiel) {
        aufgeloest = spiel.sender.associate { it.name to (if (it.an == false) null else vm.kanalVorschau(it)) }
    }

    LaunchedEffect(fokusIndex, mitVorschau) {
        val z = zeilen.getOrNull(fokusIndex) as? SpielZeile.Sender ?: return@LaunchedEffect
        if (z.sender.an == false) return@LaunchedEffect
        val ch = vm.kanalVorschau(z.sender) ?: return@LaunchedEffect
        vorschauKanal = ch
        liveVm.onChannelFocused(ch)
        if (!mitVorschau) return@LaunchedEffect
        delay(700)
        liveVm.playPreview(ch)
    }

    val ersterFokus = remember { FocusRequester() }
    LaunchedEffect(spiel.id) { if (zeilen.isNotEmpty()) fokusMitWiederholung(ersterFokus) }

    val klick: (SpielZeile) -> Unit = { z ->
        scope.launch {
            when (z) {
                is SpielZeile.Sender -> {
                    if (z.sender.an == false) return@launch
                    val ch = vm.kanalFuer(spiel.id, z.sender)
                    if (ch != null) onPlayChannel(ch) else onOpenLiveTv(null)
                }
                is SpielZeile.Marke -> onOpenLiveTv(vm.kategorieId(z.kategorie))
                is SpielZeile.Bereich -> onOpenLiveTv(vm.kategorieId(z.name))
            }
        }
    }

    val liste: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(spiel.titel, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val unter = listOfNotNull(spiel.wettbewerb, spiel.startDE.takeIf { it.isNotBlank() }).joinToString(" · ")
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (spiel.laeuft) {
                    Text(stringResource(R.string.g4k_sport_live), style = MaterialTheme.typography.labelMedium, color = SportLiveRot, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                }
                Text(unter, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            if (zeilen.isEmpty()) {
                Text(stringResource(R.string.g4k_sport_kein_kanal), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(zeilen) { i, z ->
                    ZeileKarte(
                        zeile = z,
                        kanalBekannt = (z as? SpielZeile.Sender)?.let { aufgeloest[it.sender.name] != null || !aufgeloest.containsKey(it.sender.name) } ?: true,
                        onClick = { klick(z) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) fokusIndex = i }
                            .then(if (i == 0) Modifier.focusRequester(ersterFokus) else Modifier),
                    )
                }
            }
        }
    }

    if (formfaktor.mobil) {
        Column(modifier.fillMaxSize()) {
            German4kMobilKopfzeile(titel = spiel.titel, onZurueck = onBack)
            liste(Modifier.fillMaxSize().padding(horizontal = 12.dp))
        }
    } else {
        Row(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            liste(Modifier.weight(1f).fillMaxSize())
            Spacer(Modifier.width(20.dp))
            Box(
                Modifier
                    .weight(1.2f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (mitVorschau && vorschauKanal != null) {
                    ExoPreviewSurface(engine = liveVm.previewEngine, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ZeileKarte(zeile: SpielZeile, kanalBekannt: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val aus = zeile is SpielZeile.Sender && zeile.sender.an == false
    val grau = aus || (zeile is SpielZeile.Sender && !kanalBekannt)
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        unfocusedContainerColor = colors.surfaceContainer,
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.heightIn(min = 56.dp).alpha(if (aus) 0.45f else 1f),
    ) { _ ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            when (zeile) {
                is SpielZeile.Sender -> {
                    val s = zeile.sender
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOfNotNull(s.kategorie, s.name, s.gruppeName).joinToString(" · "),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (grau) colors.onSurfaceVariant else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (s.platz && s.platzZustand != null) {
                            Spacer(Modifier.width(8.dp))
                            PlatzAbzeichen(laeuft = s.platzZustand == German4kSpiel.ZUSTAND_LAEUFT)
                        }
                    }
                    when {
                        aus -> Unterzeile(stringResource(R.string.g4k_sport_sender_aus, s.gruppeName ?: s.kategorie ?: s.name))
                        !kanalBekannt -> Unterzeile(stringResource(R.string.g4k_sport_kein_kanal))
                    }
                }
                is SpielZeile.Marke -> Text(stringResource(R.string.g4k_sport_marke, zeile.name), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                is SpielZeile.Bereich -> Text(stringResource(R.string.g4k_sport_bereich, zeile.name), style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            }
        }
    }
}

@Composable
private fun Unterzeile(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = OwnTVTheme.colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** Kleines Abzeichen am Eventplatz: LIVE (läuft) oder NEXT (geplant). */
@Composable
private fun PlatzAbzeichen(laeuft: Boolean) {
    val colors = OwnTVTheme.colors
    Text(
        stringResource(if (laeuft) R.string.g4k_sport_live else R.string.g4k_sport_next),
        style = MaterialTheme.typography.labelSmall,
        color = if (laeuft) colors.onPrimary else colors.onSecondaryContainer,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (laeuft) SportLiveRot else colors.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
