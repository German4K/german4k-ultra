package tv.own.owntv.features.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ErinnerungEntity
import tv.own.owntv.core.german4k.German4kErinnern
import tv.own.owntv.core.german4k.German4kErinnerungen
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.fokusMitWiederholung
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * German4K 3.2 (N7): „Umschalten“ aus dem Erinnerungs-Hinweis. Der Hinweis liegt in der Overlay-Schicht
 * und kennt die Shell nicht; die Shell beobachtet dieses Ziel und spielt den Sender im Vollbild — auch,
 * wenn gerade ein Film läuft. Gleiches Muster wie [tv.own.owntv.core.german4k.German4kSupport].
 */
object German4kUmschalten {
    private val _ziel = MutableStateFlow<ChannelEntity?>(null)
    val ziel: StateFlow<ChannelEntity?> = _ziel.asStateFlow()

    fun auf(channel: ChannelEntity) { _ziel.value = channel }
    fun erledigt() { _ziel.value = null }
}

private const val PRUEF_INTERVALL_MS = 15_000L

/** „Sa 15:30“ in der Sprache der App. */
@Composable
internal fun erinnerungZeit(startMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(startMs, locale) {
        DateTimeFormatter.ofPattern("EEE HH:mm", locale).format(Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()))
    }
}

/**
 * Die Wache: alle 15 s die offenen Erinnerungen des aktiven Profils ansehen und den fälligen Hinweis
 * zeigen — „⏰ Gleich: …“ oder, wenn die App zu war, „Verpasst? Läuft seit … min“. Sport wird vorher
 * frisch beim Sport-Hub nachgefragt (verlegt → wandert mit, beendet → still erledigt).
 */
@Composable
internal fun German4kErinnerungsHinweis() {
    val repo: German4kErinnerungen = koinInject()
    var aktuell by remember { mutableStateOf<ErinnerungEntity?>(null) }
    LaunchedEffect(Unit) {
        repo.weckerNachziehen()
        val nachgezogen = mutableSetOf<Long>()
        while (true) {
            if (aktuell == null) {
                runCatching {
                    val jetzt = System.currentTimeMillis()
                    val offene = repo.offeneAktiv()
                    German4kErinnern.vorbei(offene, jetzt).forEach { repo.erledigen(it.id) }
                    var naechste = German4kErinnern.naechsterHinweis(offene, jetzt)
                    if (naechste != null && naechste.art != ErinnerungEntity.ART_SENDUNG && naechste.id !in nachgezogen) {
                        nachgezogen += naechste.id
                        naechste = repo.sportNachziehen(naechste)?.takeIf {
                            val z = German4kErinnern.zustand(it, System.currentTimeMillis())
                            z is German4kErinnern.Zustand.Gleich || z is German4kErinnern.Zustand.Verpasst
                        }
                    }
                    if (naechste != null) aktuell = naechste
                }
            }
            delay(PRUEF_INTERVALL_MS)
        }
    }
    val e = aktuell ?: return
    val scope = rememberCoroutineScope()
    ErinnerungDialog(
        e = e,
        onUmschalten = {
            val ch = repo.kanalFuer(e)
            if (ch != null) {
                repo.ereignis("erinnerung_umgeschaltet", "${e.art}:${e.titel}")
                German4kUmschalten.auf(ch)
                repo.erledigen(e.id)
                aktuell = null
            }
            ch != null
        },
        onSpaeter = {
            scope.launch { repo.erledigen(e.id) }
            aktuell = null
        },
    )
}

/**
 * Das Hinweisfenster. Eigenes Dialogfenster ([G4kModal], Lehre 3.1.1 Jochen703/Thomas110): der Fokus
 * landet sicher auf „Umschalten“, die Shell dahinter kann ihn nicht wegziehen, Zurück = „Später“.
 */
@Composable
private fun ErinnerungDialog(e: ErinnerungEntity, onUmschalten: suspend () -> Boolean, onSpaeter: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var keinSender by remember(e.id) { mutableStateOf(false) }
    var laeuft by remember(e.id) { mutableStateOf(false) }
    val jetzt = System.currentTimeMillis()
    val zustand = German4kErinnern.zustand(e, jetzt)
    val uhr = remember(e.startMs) { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(e.startMs).atZone(ZoneId.systemDefault())) }
    G4kModal(onDismissRequest = onSpaeter, focus = focus) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).focusGroup(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainer).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.g4k_erinnern_gleich, e.titel),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                    color = colors.onSurface, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    e.zusatz?.let { stringResource(R.string.g4k_erinnern_zeile, it, uhr) } ?: uhr,
                    style = MaterialTheme.typography.bodyLarge, color = colors.primary, textAlign = TextAlign.Center,
                )
                if (zustand is German4kErinnern.Zustand.Verpasst) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.g4k_erinnern_verpasst, zustand.minuten), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
                if (keinSender) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.g4k_erinnern_kein_sender), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!keinSender) {
                        OwnTVButton(
                            stringResource(R.string.g4k_erinnern_umschalten),
                            onClick = {
                                if (!laeuft) {
                                    laeuft = true
                                    scope.launch {
                                        // Sport: der Sender wird JETZT über den Sport-Hub bestimmt, nie aus dem Speicher.
                                        if (!onUmschalten()) keinSender = true
                                        laeuft = false
                                    }
                                }
                            },
                            icon = OwnTVIcon.PLAY,
                            modifier = Modifier.focusRequester(focus),
                        )
                        OwnTVButton(stringResource(R.string.g4k_erinnern_spaeter), onClick = onSpaeter, style = OwnTVButtonStyle.SECONDARY)
                    } else {
                        OwnTVButton(stringResource(R.string.g4k_erinnern_spaeter), onClick = onSpaeter, modifier = Modifier.focusRequester(focus))
                    }
                }
            }
        }
    }
    // Nach „kein Sender“ hängt der Fokus am neuen einzigen Knopf.
    LaunchedEffect(keinSender) { if (keinSender) fokusMitWiederholung(focus) }
}

/** Ab Android 13: beim ersten gesetzten Wecker einmal um die Benachrichtigungs-Erlaubnis bitten. */
@Composable
internal fun German4kBenachrichtigungsErlaubnis() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val gewuenscht by German4kErinnern.erlaubnisGewuenscht.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var gefragt by remember { mutableStateOf(false) }
    LaunchedEffect(gewuenscht) {
        if (!gewuenscht || gefragt) return@LaunchedEffect
        gefragt = true
        German4kErinnern.erlaubnisGewuenscht.value = false
        val erlaubt = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!erlaubt) runCatching { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
}

/** „Meine Erinnerungen“: offene oben, erledigte grau; OK löscht. */
@Composable
internal fun German4kErinnerungenScreen(onBack: () -> Unit) {
    val repo: German4kErinnerungen = koinInject()
    val liste by repo.liste.collectAsStateWithLifecycle(initialValue = null)
    val colors = OwnTVTheme.colors
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val zuFokus = remember { FocusRequester() }
    BackHandler { onBack() }
    val l = liste
    LaunchedEffect(l?.size) {
        if (l == null) return@LaunchedEffect
        fokusMitWiederholung(if (l.isEmpty()) zuFokus else focus)
    }
    Box(
        Modifier.fillMaxSize().background(colors.background).modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 900.dp).padding(horizontal = 40.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.g4k_erinnern_liste_titel), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (l.isNullOrEmpty()) R.string.g4k_erinnern_liste_leer else R.string.g4k_erinnern_liste_text),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 620.dp),
            )
            Spacer(Modifier.height(18.dp))
            if (!l.isNullOrEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(l, key = { it.id }) { e ->
                        val vorbei = e.erledigt || German4kErinnern.zustand(e, System.currentTimeMillis()) == German4kErinnern.Zustand.Vorbei
                        FocusableSurface(
                            onClick = { scope.launch { repo.loeschen(e.id) } },
                            modifier = Modifier.fillMaxWidth().then(if (e.id == l.first().id) Modifier.focusRequester(focus) else Modifier),
                            shape = RoundedCornerShape(14.dp),
                        ) { _ ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp).alpha(if (vorbei) 0.5f else 1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.titel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    e.zusatz?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    if (vorbei) stringResource(R.string.g4k_erinnern_vorbei) else erinnerungZeit(e.startMs),
                                    style = MaterialTheme.typography.bodyMedium, color = colors.primary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            OwnTVButton(stringResource(R.string.g4k_kunde_zu), onClick = onBack, modifier = Modifier.focusRequester(zuFokus))
        }
    }
}
