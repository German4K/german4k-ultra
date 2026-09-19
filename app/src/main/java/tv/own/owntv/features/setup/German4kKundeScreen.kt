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
import androidx.compose.runtime.remember
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
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.german4k.German4kProvisioner
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.modalScrim
import tv.own.owntv.ui.components.trapAllFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * „Dein Zugang" — die vier Dinge, die am Fernseher sonst fremde Hilfe brauchen.
 *
 * Verlängern, ein zweites Gerät einrichten, jemanden werben, uns schreiben: Jedes davon endet sonst
 * in einem Chat, in dem wir einen Link schicken, den der Kunde dann am Handy öffnet. Genau das macht
 * ein QR-Code von selbst — und die Kontakt-Nachricht trägt Gerät und Version schon im Text, sodass
 * die erste Rückfrage entfällt.
 *
 * Nichts davon ist eine Werbefläche: Wer nichts braucht, drückt einmal Zurück.
 */
@Composable
fun German4kKundeScreen(onBack: () -> Unit) {
    val provisioner: German4kProvisioner = koinInject()
    val answer by provisioner.answer.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onBack() }

    val k = answer?.kunde
    Box(
        Modifier.fillMaxSize().background(colors.background).modalScrim().trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 1100.dp).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.g4k_kunde_titel), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            k?.tageOffen?.takeIf { it >= 0 }?.let {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.g4k_kunde_tage, it), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
            }
            answer?.expireDate?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.g4k_expires, it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
                QrKachel(R.string.g4k_kunde_verlaengern, R.string.g4k_kunde_verlaengern_text, k?.verlaengernUrl)
                QrKachel(R.string.g4k_kunde_zweitgeraet, R.string.g4k_kunde_zweitgeraet_text, k?.zweitgeraetUrl)
                // Ohne Kopplung gibt es keinen Einladungslink — dann fehlt die Kachel, statt leer dazustehen.
                QrKachel(R.string.g4k_kunde_werben, R.string.g4k_kunde_werben_text, k?.werbenUrl)
                QrKachel(R.string.g4k_kunde_kontakt, R.string.g4k_kunde_kontakt_text, k?.kontaktUrl)
            }

            Spacer(Modifier.height(28.dp))
            OwnTVButton(stringResource(R.string.g4k_kunde_zu), onClick = onBack, modifier = Modifier.focusRequester(focus))
        }
    }
}

/** Eine Kachel: Überschrift, QR-Code, ein Satz. Ohne Adresse erscheint sie gar nicht. */
@Composable
private fun QrKachel(titel: Int, text: Int, url: String?) {
    if (url.isNullOrBlank()) return
    val colors = OwnTVTheme.colors
    val qr = remember(url) { runCatching { CompanionLink.renderQr(url, 360) }.getOrNull() }
    Column(
        modifier = Modifier.width(230.dp).clip(RoundedCornerShape(18.dp)).background(colors.surfaceContainerHigh).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(titel), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = colors.onSurface)
        Spacer(Modifier.height(10.dp))
        qr?.let {
            Image(
                bitmap = it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(text), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
