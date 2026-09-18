package tv.own.owntv.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.german4k.German4kPanelAnswer
import tv.own.owntv.core.german4k.German4kProvisioner
import tv.own.owntv.core.german4k.German4kSupport
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * The German4K message layer over the shell: a hint dialog when the panel has something to say
 * (access expiring → renew with QR, maintenance, info) and a full-screen lock when the access is
 * not active. Maintenance shows on every start; everything else once a day after "Got it".
 */
@Composable
fun German4kOverlays() {
    val provisioner: German4kProvisioner = koinInject()
    val answer by provisioner.answer.collectAsStateWithLifecycle()
    val state by provisioner.state.collectAsStateWithLifecycle()
    val hilfe by German4kSupport.sichtbar.collectAsStateWithLifecycle()
    if (hilfe) {
        German4kSupportScreen(onBack = German4kSupport::schliessen)
        return
    }
    val a = answer ?: return
    val scope = rememberCoroutineScope()
    var hidden by remember { mutableStateOf<String?>(null) }
    var seen by remember(a.noteId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(a.noteId) { seen = if (a.noteId.isBlank()) true else provisioner.hinweisSchonGesehen(a.noteId) }

    val locked = a.locked || state is German4kProvisioner.State.Locked || (a.noteTyp == "abgelaufen" && a.sources.isEmpty())
    if (locked) {
        LockOverlay(a, onRetry = provisioner::provision)
        return
    }
    // Uhrzeit: der Programmführer sieht bei falscher Gerätezeit verschoben aus, und niemand sucht
    // den Fehler in der Uhr des Fernsehers. Einmal am Tag, und nur wenn es wirklich weit daneben ist.
    val uhrId = remember(a.serverTime) { uhrHinweisId(a) }
    var uhrGesehen by remember(uhrId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(uhrId) { uhrGesehen = if (uhrId == null) true else provisioner.hinweisSchonGesehen(uhrId) }
    if (uhrId != null && uhrGesehen == false && hidden != uhrId) {
        UhrHinweis(onOk = { hidden = uhrId; scope.launch { provisioner.hinweisGesehen(uhrId) } })
        return
    }

    val showTypes = setOf("verlaengern", "wartung", "info", "abgelaufen")
    if (a.noteTyp !in showTypes || a.noteContent.isBlank()) return
    val mustShow = a.noteTyp == "wartung"
    if (hidden == a.noteId || (!mustShow && seen != false)) return

    HintDialog(
        a,
        onOk = { hidden = a.noteId; scope.launch { provisioner.hinweisGesehen(a.noteId) } },
        onLater = { hidden = a.noteId },
    )
}

/** Hint id for a device clock that is more than five minutes away from ours, or null when it is fine. */
private fun uhrHinweisId(a: German4kPanelAnswer): String? {
    if (a.serverTime.isBlank()) return null
    val server = runCatching { java.time.Instant.parse(a.serverTime).toEpochMilli() }.getOrNull() ?: return null
    val abweichung = kotlin.math.abs(System.currentTimeMillis() - server) / 1000
    return if (abweichung >= 300) "uhr:${java.time.LocalDate.now()}" else null
}

@Composable
private fun UhrHinweis(onOk: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onOk() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainer).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.g4k_fehler_uhrzeit_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.g4k_uhr_hinweis), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            OwnTVButton(stringResource(R.string.g4k_hint_ok), onClick = onOk, modifier = Modifier.focusRequester(focus))
        }
    }
}

@Composable
private fun HintDialog(a: German4kPanelAnswer, onOk: () -> Unit, onLater: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onLater() }
    val renew = a.noteTyp == "verlaengern" || a.noteTyp == "abgelaufen"
    val qrUrl = if (renew) a.verlaengernUrl else ""
    val qr = remember(qrUrl) { qrUrl.takeIf { it.isNotBlank() }?.let { CompanionLink.renderQr(it, 360) } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).focusGroup(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 820.dp).clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainer).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(a.noteTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = colors.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(a.noteContent, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = if (qr != null) TextAlign.Start else TextAlign.Center, modifier = Modifier.widthIn(max = if (qr != null) 460.dp else 700.dp))
                qr?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                            modifier = Modifier.size(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(6.dp))
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.g4k_hint_qr_renew), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.width(220.dp))
                    }
                }
            }
            if (renew && a.expireDate.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.g4k_expires, a.expireDate), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.g4k_hint_ok), onClick = onOk, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(focus))
                if (a.noteTyp != "wartung") OwnTVButton(stringResource(R.string.g4k_hint_later), onClick = onLater, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}

@Composable
private fun LockOverlay(a: German4kPanelAnswer, onRetry: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { }
    val qr = remember(a.verlaengernUrl) { a.verlaengernUrl.takeIf { it.isNotBlank() }?.let { CompanionLink.renderQr(it, 400) } }
    Box(Modifier.fillMaxSize().background(colors.background).focusGroup(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = 900.dp).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(a.noteTitle.ifBlank { stringResource(R.string.g4k_locked_title) }, style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(10.dp))
            Text(a.noteContent, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 700.dp))
            qr?.let {
                Spacer(Modifier.height(18.dp))
                Image(bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.size(176.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(8.dp))
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.g4k_hint_qr_renew), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.g4k_contact), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton(stringResource(R.string.g4k_retry_button_short), onClick = onRetry, modifier = Modifier.focusRequester(focus))
                OwnTVButton(stringResource(R.string.g4k_help_open), onClick = German4kSupport::oeffnen, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}

object German4kDays {
/** Days until the access expires, or null when unknown / more than [maxDays] away. */
fun German4kPanelAnswer.daysLeft(maxDays: Int = 7): Int? {
    val d = runCatching { java.time.LocalDate.parse(expireDate) }.getOrNull() ?: return null
    val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), d).toInt()
    return days.takeIf { it in 0..maxDays }
}
}
