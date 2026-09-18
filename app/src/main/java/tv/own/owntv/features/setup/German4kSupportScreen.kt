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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.CoreBuildInfo
import tv.own.owntv.core.german4k.German4kDeviceId
import tv.own.owntv.core.german4k.German4kDiagnose
import tv.own.owntv.core.german4k.German4kHealth
import tv.own.owntv.core.german4k.German4kPanelClient
import tv.own.owntv.core.german4k.German4kProvisioner
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * "Hilfe & Verbindung" — the screen support can name in one sentence.
 *
 * Today a black picture costs four messages: what does it say, is a second device running, which
 * WiFi, send a photo. All four are things the device knows. This screen measures its own way out and
 * says one plain sentence about it; the second button sends the whole picture to us, so the next
 * message is an answer rather than another question.
 */
@Composable
fun German4kSupportScreen(onBack: () -> Unit) {
    val provisioner: German4kProvisioner = koinInject()
    val health: German4kHealth = koinInject()
    val panel: German4kPanelClient = koinInject()
    val context = LocalContext.current
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    val answer by provisioner.answer.collectAsStateWithLifecycle()

    var laeuft by remember { mutableStateOf(false) }
    var befund by remember { mutableStateOf<German4kHealth.Befund?>(null) }
    var gesendet by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    // Ohne Fokusfalle bedient die Fernbedienung weiter die Shell darunter (die Suche fing die Tasten
    // ab, der Knopf sah nur fokussiert aus) — dieselbe Absicherung wie in OwnTVs eigenen Dialogen.
    BackHandler { onBack() }
    Box(
        Modifier.fillMaxSize().background(colors.background).modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 900.dp).padding(40.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.g4k_help_title), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.g4k_help_subtitle),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 640.dp),
            )

            Spacer(Modifier.height(20.dp))
            answer?.let { a ->
                Text(
                    stringResource(R.string.g4k_help_device, a.mac, a.deviceKey),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.g4k_help_build, CoreBuildInfo.versionName, CoreBuildInfo.versionCode),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )

            // --- verdict -------------------------------------------------------------------
            befund?.let { b ->
                Spacer(Modifier.height(22.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(20.dp),
                ) {
                    Text(klasseTitel(b.klasse), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(klasseText(b.klasse), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    b.schritte.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Box(
                                Modifier.size(10.dp).clip(CircleShape)
                                    .background(if (s.ok) colors.primary else colors.favorite),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(schrittZeile(s), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            if (laeuft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OwnTVSpinner(sizeDp = 24)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.g4k_help_checking), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton(
                        stringResource(R.string.g4k_help_check),
                        onClick = {
                            laeuft = true
                            scope.launch {
                                val b = health.pruefe(answer)
                                befund = b
                                laeuft = false
                                // The result goes to us as well: a self-test nobody can read is a
                                // self-test that still ends in a chat message.
                                German4kDiagnose.sendeJetzt(
                                    context, panel, German4kDeviceId.get(context),
                                    German4kDiagnose.TYP_SELBSTTEST, b.bericht(),
                                )
                            }
                        },
                        modifier = Modifier.focusRequester(focus),
                    )
                    OwnTVButton(
                        stringResource(if (gesendet) R.string.g4k_help_sent else R.string.g4k_help_send),
                        onClick = {
                            German4kDiagnose.sendeJetzt(context, panel, German4kDeviceId.get(context))
                            gesendet = true
                        },
                        style = OwnTVButtonStyle.SECONDARY,
                        icon = OwnTVIcon.DOWNLOADS,
                    )
                    OwnTVButton(stringResource(R.string.g4k_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.g4k_help_contact), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Step label in the customer's language; the report we receive keeps the German wording. */
@Composable
private fun schrittZeile(s: German4kHealth.Schritt): String {
    val res = when (s.schluessel) {
        German4kHealth.KEY_NETZ -> R.string.g4k_schritt_netz
        German4kHealth.KEY_INTERNET -> R.string.g4k_schritt_internet
        German4kHealth.KEY_PANEL -> R.string.g4k_schritt_panel
        German4kHealth.KEY_HOST -> R.string.g4k_schritt_host
        German4kHealth.KEY_STREAM -> R.string.g4k_schritt_stream
        else -> R.string.g4k_schritt_uhr
    }
    val name = stringResource(res)
    val voll = if (s.zusatz.isBlank()) name else "$name ${s.zusatz}"
    return "$voll: ${s.info}"
}

@Composable
private fun klasseTitel(k: German4kHealth.Klasse): String = stringResource(
    when (k) {
        German4kHealth.Klasse.ALLES_GUT -> R.string.g4k_fehler_alles_gut_title
        German4kHealth.Klasse.KEIN_NETZ -> R.string.g4k_fehler_kein_netz_title
        German4kHealth.Klasse.ZWANGSPORTAL -> R.string.g4k_fehler_zwangsportal_title
        German4kHealth.Klasse.ROUTER_SPERRE -> R.string.g4k_fehler_router_sperre_title
        German4kHealth.Klasse.ANBIETER_SPERRE -> R.string.g4k_fehler_anbieter_sperre_title
        German4kHealth.Klasse.HOST_AUSFALL -> R.string.g4k_fehler_host_ausfall_title
        German4kHealth.Klasse.LEITUNG_BELEGT -> R.string.g4k_fehler_leitung_belegt_title
        German4kHealth.Klasse.ABGELAUFEN -> R.string.g4k_fehler_abgelaufen_title
        German4kHealth.Klasse.LAND_GESPERRT -> R.string.g4k_fehler_land_gesperrt_title
        German4kHealth.Klasse.UHRZEIT -> R.string.g4k_fehler_uhrzeit_title
        German4kHealth.Klasse.UNBEKANNT -> R.string.g4k_fehler_unbekannt_title
    },
)

@Composable
private fun klasseText(k: German4kHealth.Klasse): String = stringResource(
    when (k) {
        German4kHealth.Klasse.ALLES_GUT -> R.string.g4k_fehler_alles_gut_body
        German4kHealth.Klasse.KEIN_NETZ -> R.string.g4k_fehler_kein_netz_body
        German4kHealth.Klasse.ZWANGSPORTAL -> R.string.g4k_fehler_zwangsportal_body
        German4kHealth.Klasse.ROUTER_SPERRE -> R.string.g4k_fehler_router_sperre_body
        German4kHealth.Klasse.ANBIETER_SPERRE -> R.string.g4k_fehler_anbieter_sperre_body
        German4kHealth.Klasse.HOST_AUSFALL -> R.string.g4k_fehler_host_ausfall_body
        German4kHealth.Klasse.LEITUNG_BELEGT -> R.string.g4k_fehler_leitung_belegt_body
        German4kHealth.Klasse.ABGELAUFEN -> R.string.g4k_fehler_abgelaufen_body
        German4kHealth.Klasse.LAND_GESPERRT -> R.string.g4k_fehler_land_gesperrt_body
        German4kHealth.Klasse.UHRZEIT -> R.string.g4k_fehler_uhrzeit_body
        German4kHealth.Klasse.UNBEKANNT -> R.string.g4k_fehler_unbekannt_body
    },
)
