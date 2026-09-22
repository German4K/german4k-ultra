package tv.own.owntv.features.mobil

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 * ein Drittel der Breite, und niemand bedient sie mit dem Daumen. Fuenf Ziele sind das Maximum,
 * das nebeneinander noch lesbar ist — deshalb nur die vier meistgenutzten Bereiche plus "Mehr";
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
        listOf(MainSection.HOME, MainSection.LIVE_TV, MainSection.MOVIES, MainSection.SERIES)
            .filter { it in visibleSections } + MainSection.MORE
    }
    Column(modifier = modifier.fillMaxWidth().height(German4kBottomBarHoehe)) {
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
                FocusableSurface(
                    onClick = { onSelect(section) },
                    selected = aktiv,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
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
                        Text(
                            text = stringResource(section.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = ton,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
