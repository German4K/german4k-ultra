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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.german4k.German4kBereich
import tv.own.owntv.core.german4k.German4kDeviceId
import tv.own.owntv.core.german4k.German4kPanelClient
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * „Länder & Bereiche" — die Senderliste kürzen, ohne uns zu fragen.
 *
 * Drei der häufigsten Bitten im Chat sind dieselbe Sache: die Liste ist zu lang, ein Land soll weg,
 * ein anderes dazu. Jede davon kostet heute eine Runde Nachrichten und einen Klick von uns, obwohl
 * der Kunde es selbst kann — er weiß nur nicht, wo.
 *
 * Die Senderzahl steht bewusst an jeder Zeile: Wer etwas abschaltet, soll sehen, was verschwindet.
 * Den Riegel gegen die leer geräumte Liste hält das Panel (`genugUebrig`), nicht diese Oberfläche.
 */
@Composable
fun German4kBereicheScreen(onBack: () -> Unit) {
    val panel: German4kPanelClient = koinInject()
    val context = LocalContext.current
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()

    var liste by remember { mutableStateOf<List<German4kBereich>>(emptyList()) }
    var laedt by remember { mutableStateOf(true) }
    var grund by remember { mutableStateOf<String?>(null) }
    var geaendert by remember { mutableStateOf(false) }
    var fokusGesetzt by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val r = panel.bereiche(German4kDeviceId.get(context), CoreBuildInfo.versionName)
        liste = r.liste
        grund = r.grund
        laedt = false
    }
    // Erst wenn die erste Zeile wirklich im Baum hängt, darf der Fokus dorthin — ein
    // requestFocus() direkt nach der Antwort läuft eine Bildfolge zu früh und verpufft still,
    // und dann liegt der Fokus nirgends: die Fernbedienung bewegt nichts mehr.
    LaunchedEffect(liste.isEmpty()) {
        if (liste.isNotEmpty() && !fokusGesetzt) {
            fokusGesetzt = runCatching { focus.requestFocus() }.isSuccess
        }
    }
    BackHandler { onBack() }

    Box(
        Modifier.fillMaxSize().background(colors.background).modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 900.dp).padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.g4k_bereiche_titel), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.g4k_bereiche_text),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 620.dp),
            )
            grund?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary, textAlign = TextAlign.Center)
            }
            if (geaendert) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.g4k_bereiche_neu), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }

            Spacer(Modifier.height(18.dp))
            when {
                laedt -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OwnTVSpinner(sizeDp = 24)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.g4k_bereiche_laedt), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                liste.isEmpty() -> Text(
                    stringResource(R.string.g4k_bereiche_leer),
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(liste, key = { it.id }) { b ->
                        BereichZeile(
                            bereich = b,
                            modifier = if (b.id == liste.first().id) Modifier.focusRequester(focus) else Modifier,
                            onToggle = {
                                scope.launch {
                                    // Erst umschalten, dann die Antwort übernehmen: sie trägt den
                                    // Stand, den der Server wirklich gespeichert hat.
                                    val r = panel.bereiche(German4kDeviceId.get(context), CoreBuildInfo.versionName, b.id, !b.an)
                                    if (r.liste.isNotEmpty()) liste = r.liste
                                    grund = r.grund
                                    if (r.ok) geaendert = true
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            OwnTVButton(stringResource(R.string.g4k_kunde_zu), onClick = onBack)
        }
    }
}

@Composable
private fun BereichZeile(bereich: German4kBereich, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    ) { _ ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (bereich.an) colors.primary else colors.outlineVariant),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                bereich.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (bereich.an) FontWeight.Bold else FontWeight.Normal,
                color = if (bereich.an) colors.onSurface else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.g4k_bereiche_sender, bereich.sender),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
        }
    }
}
