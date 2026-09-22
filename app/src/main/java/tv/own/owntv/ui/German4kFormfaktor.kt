package tv.own.owntv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.german4k.German4kDeviceCaps
import tv.own.owntv.core.settings.G4kDarstellung
import tv.own.owntv.core.settings.SettingsRepository

/**
 * German4K: Formfaktor fuer Handy-/Tablet-Layout (22.09.2026).
 *
 * Breite in dp, wie Material Adaptive sie einteilt: unter 600 ein Handy im Hochformat (KOMPAKT),
 * bis 840 ein Handy quer oder kleines Tablet (MITTEL), darueber ein grosses Tablet oder ein
 * Fernseher (WEIT). Der Fernseher bleibt unveraendert — er landet immer in WEIT und mobil=false.
 */
enum class Breitenklasse { KOMPAKT, MITTEL, WEIT }

fun klasseFuer(breiteDp: Int): Breitenklasse = when {
    breiteDp < 600 -> Breitenklasse.KOMPAKT
    breiteDp < 840 -> Breitenklasse.MITTEL
    else -> Breitenklasse.WEIT
}

/**
 * Ergebnis der Formfaktor-Erkennung: mobil (untere Leiste statt Seitenleiste) + Breitenklasse
 * (fuer feineres Layout innerhalb von mobil, z. B. Spaltenzahl). [TV] ist der unveraenderte
 * Fernseher-Fall und der Startwert von [LocalFormfaktor], solange noch nichts anderes bereitgestellt wurde.
 */
@Immutable
data class German4kFormfaktor(val mobil: Boolean, val klasse: Breitenklasse) {
    val kompakt: Boolean get() = mobil && klasse == Breitenklasse.KOMPAKT

    companion object {
        val TV = German4kFormfaktor(mobil = false, klasse = Breitenklasse.WEIT)
    }
}

/**
 * [darstellung] ist der Schalter aus den Einstellungen (Automatisch/Fernseher/Handy) — er gewinnt
 * immer gegen die erkannte Geraeteart [tv], damit eine falsch erkannte Box sich uebersteuern laesst.
 */
fun formfaktorFuer(tv: Boolean, darstellung: G4kDarstellung, breiteDp: Int): German4kFormfaktor {
    val mobil = when (darstellung) {
        G4kDarstellung.TV -> false
        G4kDarstellung.MOBIL -> true
        G4kDarstellung.AUTO -> !tv
    }
    return German4kFormfaktor(mobil = mobil, klasse = klasseFuer(breiteDp))
}

/** Bereitgestellt von MainActivity um den bisherigen Inhalt; Standard TV, falls nichts bereitgestellt wurde. */
val LocalFormfaktor = compositionLocalOf { German4kFormfaktor.TV }

@Composable
fun rememberGerman4kFormfaktor(): German4kFormfaktor {
    val context = LocalContext.current
    val tv = remember(context) { German4kDeviceCaps.get(context).tv }
    val settings = koinInject<SettingsRepository>()
    // initialValue AUTO: beim allerersten Frame ist der Schalter noch nicht gelesen (kein
    // runBlocking — das waere ein Kaltstart-ANR auf API 28). Auf einem Handy mit erzwungenem TV
    // flackert dann ein Frame lang die Bottom-Bar; hinnehmbar.
    val darstellung by settings.g4kDarstellung.collectAsStateWithLifecycle(initialValue = G4kDarstellung.AUTO)
    // screenWidthDp folgt der Drehung — Emulator und echte Geraete liefern beim Drehen eine neue Configuration.
    val breite = LocalConfiguration.current.screenWidthDp
    return remember(tv, darstellung, breite) { formfaktorFuer(tv, darstellung, breite) }
}
