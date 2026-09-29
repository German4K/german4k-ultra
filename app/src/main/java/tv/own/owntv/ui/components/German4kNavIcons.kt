package tv.own.owntv.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import tv.own.owntv.core.nav.MainSection

// German4K 3.0 (31): Die Navigation zeichnet ihre Symbole nicht mehr selbst, sondern nimmt
// Material Symbols — im Ruhezustand die Outlined-Form, ausgewaehlt die gefuellte Rounded-Form.
// Entscheidung Betreiber 29.09.2026: gefuellt = "hier bist du", Umriss = "hier kannst du hin".
// Eine einzige Zuordnung fuer Seitenleiste (TV), untere Leiste (Handy), "Mehr" und das
// Menue-Einstellungsblatt, damit dasselbe Ziel ueberall dasselbe Zeichen traegt.
fun MainSection.navVector(selected: Boolean): ImageVector = when (this) {
    MainSection.HOME -> if (selected) Icons.Rounded.Home else Icons.Outlined.Home
    MainSection.LIVE_TV -> if (selected) Icons.Rounded.LiveTv else Icons.Outlined.LiveTv
    MainSection.SPORT -> if (selected) Icons.Rounded.SportsSoccer else Icons.Outlined.SportsSoccer
    MainSection.MOVIES -> if (selected) Icons.Rounded.Movie else Icons.Outlined.Movie
    MainSection.SERIES -> if (selected) Icons.Rounded.VideoLibrary else Icons.Outlined.VideoLibrary
    MainSection.DOWNLOADS -> if (selected) Icons.Rounded.Download else Icons.Outlined.Download
    MainSection.EPG -> if (selected) Icons.Rounded.ViewTimeline else Icons.Outlined.ViewTimeline
    MainSection.MORE -> if (selected) Icons.Rounded.Menu else Icons.Outlined.Menu
    MainSection.SETTINGS -> if (selected) Icons.Rounded.Settings else Icons.Outlined.Settings
    MainSection.SEARCH -> if (selected) Icons.Rounded.Search else Icons.Outlined.Search
}

/** German4K: das Navigationssymbol, eingefaerbt in [color]. */
@Composable
fun German4kNavIcon(
    section: MainSection,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Image(
        imageVector = section.navVector(selected),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = modifier,
    )
}
