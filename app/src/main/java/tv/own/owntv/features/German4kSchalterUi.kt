package tv.own.owntv.features

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import tv.own.owntv.core.german4k.German4kFeatures
import tv.own.owntv.core.german4k.German4kKonfig

/**
 * German4K 3.2: Notschalter im Bild. Liest den Flow, damit eine frische Panel-Antwort die Oberfläche sofort
 * umstellt (nicht erst beim nächsten Öffnen). Regel wie [German4kFeatures.an]: fehlt = an, nur "aus" = alt.
 */
@Composable
fun german4kAn(name: String): Boolean {
    val features by German4kFeatures.flow.collectAsState()
    return features[name] != "aus"
}

/** Server-Text aus `konfig.texte` für [schluessel], sonst [appText] (strings.xml). */
@Composable
fun german4kText(schluessel: String, appText: String): String {
    val konfig by German4kKonfig.flow.collectAsState()
    return konfig.texte[schluessel] ?: appText
}
