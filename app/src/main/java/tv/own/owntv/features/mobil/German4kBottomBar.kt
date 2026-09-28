package tv.own.owntv.features.mobil

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.NavDuotoneIcon
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Dasselbe Symbol wie in der Seitenleiste, fuer Stellen, die ein [OwnTVIcon] statt der
 * Canvas-Zeichnung brauchen (die Zusatzzeilen in "Mehr"). Bewusst eine eigene Zuordnung: die der
 * Sidebar ist `private` und soll es bleiben.
 */
internal val MainSection.german4kNavIcon: OwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> OwnTVIcon.SEARCH
        MainSection.HOME -> OwnTVIcon.HOME
        MainSection.LIVE_TV -> OwnTVIcon.LIVE_TV
        MainSection.SPORT -> OwnTVIcon.LIVE_TV // German4K: kein eigenes Glyph; die Leiste zeichnet den Ball (NavDuotoneIcon)
        MainSection.MOVIES -> OwnTVIcon.MOVIES
        MainSection.SERIES -> OwnTVIcon.SERIES
        MainSection.DOWNLOADS -> OwnTVIcon.DOWNLOADS
        MainSection.EPG -> OwnTVIcon.EPG
        MainSection.SETTINGS -> OwnTVIcon.SETTINGS
        MainSection.MORE -> OwnTVIcon.MORE
    }

/** Hoehe der unteren Leiste — die Shell zieht den Inhalt darueber auf `weight(1f)`. */
val German4kBottomBarHoehe = 72.dp

/**
 * German4K: die untere Leiste, die auf Handy und Tablet an die Stelle der Seitenleiste tritt
 * (22.09.2026).
 *
 * Warum eine eigene Leiste statt der Sidebar: auf einem Hochformat-Handy frisst die Seitenleiste
 * ein Drittel der Breite, und niemand bedient sie mit dem Daumen. Fuenf Ziele waren das Maximum,
 * seit 3.0 kommt Fußball als sechstes dazu (kleinere Schrift) — die meistgenutzten Bereiche plus "Mehr";
 * Suche, Downloads und TV-Programm haengen in Mobil unter "Mehr" (siehe MoreScreen.zusatzSections).
 *
 * "Mehr" bleibt hell, waehrend die Einstellungen offen sind — genau wie in der Sidebar, denn dort
 * ist der Nutzer: eine Ebene tiefer hinter diesem Eintrag.
 *
 * Bewusst ohne `focusRestorer`: die Leiste ist eine flache Zeile aus fuenf gleichen Feldern, da
 * gibt es nichts wiederherzustellen. Ein `focusGroup()` reicht, damit sie auf einer Android-Box
 * ohne Touch per Steuerkreuz erreichbar bleibt.
 */
@Composable
fun German4kBottomBar(
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val eintraege = remember(visibleSections) {
        // German4K 3.0: Fußball steht zwischen Live TV und Filme (Entscheidung Betreiber). Ist das
        // Feature aus, fehlt SPORT in visibleSections und die Leiste hat wieder fuenf Felder.
        listOf(MainSection.HOME, MainSection.LIVE_TV, MainSection.SPORT, MainSection.MOVIES, MainSection.SERIES)
            .filter { it in visibleSections } + MainSection.MORE
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            // German4K: Ab Android 15 zeichnet das System randlos — ohne diesen Abstand liegt die
            // Leiste unter der Navigationsleiste bzw. dem Wischbalken, und die untere Haelfte der
            // fuenf Felder ist nicht mehr zu treffen. Der Fuellton steht bewusst VOR dem Abstand,
            // damit der Streifen unter dem Wischbalken mitgefaerbt wird statt durchzuscheinen.
            // Die Leiste bleibt 72 dp hoch, der Abstand kommt darunter dazu; auf Android 14 und
            // aelter ist er 0.
            .background(colors.surfaceContainerLow)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(German4kBottomBarHoehe),
    ) {
        // Trennlinie nach oben: ohne sie verschwimmt die Leiste mit dem Inhalt darueber.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.outlineVariant),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(colors.surfaceContainerLow)
                .focusGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            eintraege.forEach { section ->
                val aktiv = section == selected ||
                    (section == MainSection.MORE && selected == MainSection.SETTINGS)
                val ton = if (aktiv) colors.primary else colors.onSurfaceVariant
                // German4K: Ein Tipp bewegt den Fokus nicht — der Steuerkreuz-Rahmen blieb auf dem
                // zuletzt fokussierten Feld stehen (z. B. "Home" umrandet, waehrend "Filme" laeuft).
                // Beim Tipp holen wir ihn deshalb selbst auf das getippte Feld. runCatching, weil
                // requestFocus wirft, solange der Knoten noch nicht haengt.
                val fokus = remember(section) { FocusRequester() }
                FocusableSurface(
                    onClick = { runCatching { fokus.requestFocus() }; onSelect(section) },
                    selected = aktiv,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().focusRequester(fokus),
                ) { _ ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        NavDuotoneIcon(
                            section = section,
                            color = ton,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        // German4K: bei sechs Feldern auf 360 dp bleiben ~60 dp je Feld — eine Zeile,
                        // etwas kleiner, notfalls Auslassungspunkte statt Umbruch.
                        Text(
                            text = stringResource(section.labelRes),
                            style = if (eintraege.size > 5) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp) else MaterialTheme.typography.labelSmall,
                            color = ton,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
