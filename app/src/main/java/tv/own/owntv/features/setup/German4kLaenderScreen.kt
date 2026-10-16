package tv.own.owntv.features.setup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.german4k.German4kDeviceId
import tv.own.owntv.core.german4k.German4kLaenderAntwort
import tv.own.owntv.core.german4k.German4kLaenderSchalter
import tv.own.owntv.core.german4k.German4kLand
import tv.own.owntv.core.german4k.German4kLandBereich
import tv.own.owntv.core.german4k.German4kPanelClient
import tv.own.owntv.core.german4k.German4kProvisioner
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.fokusMitWiederholung
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * German4K 3.2 (N8/F6): „Länder & Bereiche“ wie der Konfigurator im Kundenportal.
 *
 * Alle Länder stehen da, auch ausgeschaltete (Panel voll, alles im Preis; Nicht-Fokus-Länder nie
 * verstecken). Je Land die Schalter Live · Filme · Serien — nur die Typen, die es dort gibt —, darunter
 * aufklappbar die einzelnen Bereiche. Das Sonderland „weitere“ (★) hat keine Länderschalter.
 *
 * Nach jedem Schalten zeigt die Seite die Meldung des Servers („Fertig – 🇬🇧 Live: 312 Sender kommen
 * dazu“) und stößt SOFORT den Abgleich an (`bereiche_stand` ist neu) — kein 12-h-Warten, kein Knopf
 * „Erneut synchronisieren“ (Emredncr58, 12.10.2026). Scheitert es, steht der Grund des Servers da und der
 * alte Stand bleibt. Die Riegel (nie fast leer, Erwachsenes nie hier) hält der Server.
 */
@Composable
internal fun German4kLaenderScreen(onBack: () -> Unit, onKeineLaender: () -> Unit) {
    val panel: German4kPanelClient = koinInject()
    val provisioner: German4kProvisioner = koinInject()
    val context = LocalContext.current
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    val fehlerText = stringResource(R.string.g4k_laender_fehler)

    var laender by remember { mutableStateOf<List<German4kLand>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var schaltet by remember { mutableStateOf(false) }
    var meldung by remember { mutableStateOf<String?>(null) }
    var warnung by remember { mutableStateOf<String?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var offen by remember { mutableStateOf<Set<String>>(emptySet()) }
    val ersterFokus = remember { FocusRequester() }
    val zuFokus = remember { FocusRequester() }

    fun uebernehmen(a: German4kLaenderAntwort) {
        if (a.laender.isNotEmpty()) laender = a.laender
        if (a.ok) {
            meldung = a.meldung
            warnung = a.warnung
            fehler = null
        } else {
            meldung = null
            warnung = null
            fehler = a.grund ?: fehlerText
        }
    }

    LaunchedEffect(Unit) {
        val a = panel.laender(German4kDeviceId.get(context), CoreBuildInfo.versionName)
        laedt = false
        // Ein Server ohne Länder-Ansicht antwortet ok, aber ohne `laender` → die alte flache Liste.
        if (a.ok && a.laender.isEmpty()) { onKeineLaender(); return@LaunchedEffect }
        uebernehmen(a)
    }
    // Fokus erst, wenn die erste Karte im Baum hängt (ein früher requestFocus() verpufft still).
    LaunchedEffect(laender.isEmpty(), laedt) {
        if (laedt) return@LaunchedEffect
        fokusMitWiederholung(if (laender.isEmpty()) zuFokus else ersterFokus)
    }
    BackHandler { onBack() }

    val schalten: (German4kLaenderSchalter) -> Unit = { s ->
        if (!schaltet) {
            schaltet = true
            scope.launch {
                val a = panel.laender(German4kDeviceId.get(context), CoreBuildInfo.versionName, s)
                uebernehmen(a)
                // Sofort neu holen: der Server hat `bereiche_stand` hochgesetzt. force übergeht die 30-s-Sperre,
                // ein laufender Lauf bekommt einen Nachlauf (mehrere Schalter hintereinander gehen nicht verloren).
                if (a.ok) provisioner.provision(force = true)
                schaltet = false
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(colors.background).modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 1000.dp).padding(horizontal = 40.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.g4k_bereiche_titel), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.g4k_bereiche_text),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 640.dp),
            )
            val status = fehler ?: meldung
            if (status != null || schaltet) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (schaltet) { OwnTVSpinner(sizeDp = 18); Spacer(Modifier.width(8.dp)) }
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary, textAlign = TextAlign.Center) }
                }
            }
            warnung?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            when {
                laedt -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OwnTVSpinner(sizeDp = 24)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.g4k_bereiche_laedt), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                laender.isEmpty() -> Text(
                    stringResource(R.string.g4k_bereiche_leer),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(laender, key = { it.land }) { land ->
                        LandKarte(
                            land = land,
                            aufgeklappt = land.land in offen,
                            onAufklappen = { offen = if (land.land in offen) offen - land.land else offen + land.land },
                            onLand = { typ, an -> schalten(German4kLaenderSchalter.Land(land.land, typ, an)) },
                            onBereich = { b -> schalten(German4kLaenderSchalter.Bereich(b.id, b.typ, !b.an)) },
                            ersterFokus = if (land.land == laender.first().land) ersterFokus else null,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OwnTVButton(stringResource(R.string.g4k_kunde_zu), onClick = onBack, modifier = Modifier.focusRequester(zuFokus))
        }
    }
}

@Composable
private fun typLabel(typ: String): String = stringResource(
    when (typ) {
        German4kLaenderAntwort.TYP_MOVIE -> R.string.g4k_laender_filme
        German4kLaenderAntwort.TYP_SERIES -> R.string.g4k_laender_serien
        else -> R.string.g4k_laender_live
    },
)

/**
 * Eine Länderkarte. D-Pad: links/rechts über die Schalter einer Karte, hoch/runter von Karte zu Karte
 * (eine Reihe je Karte, aufgeklappte Bereiche darunter als eigene Zeilen).
 */
@Composable
private fun LandKarte(
    land: German4kLand,
    aufgeklappt: Boolean,
    onAufklappen: () -> Unit,
    onLand: (typ: String, an: Boolean) -> Unit,
    onBereich: (German4kLandBereich) -> Unit,
    ersterFokus: FocusRequester?,
) {
    val colors = OwnTVTheme.colors
    val typen = German4kLaenderAntwort.LAND_TYPEN.filter { it in land.typen }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().focusGroup(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(land.flagge, land.name).joinToString(" "),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            var erster = true
            if (land.hatLandSchalter) {
                typen.forEach { typ ->
                    val t = land.typen.getValue(typ)
                    val f = ersterFokus?.takeIf { erster }
                    erster = false
                    Spacer(Modifier.width(8.dp))
                    SchalterChip(
                        text = typLabel(typ) + " · " + stringResource(if (t.an) R.string.g4k_laender_an else R.string.g4k_laender_aus),
                        unter = stringResource(R.string.g4k_bereiche_sender, t.sender),
                        an = t.an,
                        onClick = { onLand(typ, !t.an) },
                        modifier = if (f != null) Modifier.focusRequester(f) else Modifier,
                    )
                }
            }
            if (land.bereiche.isNotEmpty()) {
                val f = ersterFokus?.takeIf { erster }
                Spacer(Modifier.width(8.dp))
                SchalterChip(
                    text = (if (aufgeklappt) "▾ " else "▸ ") + stringResource(if (aufgeklappt) R.string.g4k_laender_bereiche_zu else R.string.g4k_laender_bereiche_auf),
                    unter = null,
                    an = aufgeklappt,
                    onClick = onAufklappen,
                    modifier = if (f != null) Modifier.focusRequester(f) else Modifier,
                )
            }
        }
        if (aufgeklappt) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                land.bereiche.forEach { b ->
                    FocusableSurface(onClick = { onBereich(b) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { _ ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(if (b.an) colors.primary else colors.outlineVariant))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                b.name, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (b.an) FontWeight.Bold else FontWeight.Normal,
                                color = if (b.an) colors.onSurface else colors.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                            )
                            Text(typLabel(b.typ), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.g4k_bereiche_sender, b.sender), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchalterChip(text: String, unter: String?, an: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    FocusableSurface(onClick = onClick, selected = an, shape = RoundedCornerShape(12.dp), modifier = modifier) { _ ->
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = if (an) FontWeight.Bold else FontWeight.Normal, color = if (an) colors.primary else colors.onSurfaceVariant, maxLines = 1)
            unter?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1) }
        }
    }
}
