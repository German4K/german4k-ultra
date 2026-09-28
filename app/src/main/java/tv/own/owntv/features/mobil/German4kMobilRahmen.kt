package tv.own.owntv.features.mobil

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.features.shell.components.CategoryRail
import tv.own.owntv.features.shell.components.RailCategory
import tv.own.owntv.ui.Breitenklasse
import tv.own.owntv.ui.LocalFormfaktor
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.SearchBar
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * German4K: der gemeinsame Rahmen fuer Filme, Serien und Live auf Handy und Tablet (22.09.2026).
 *
 * Warum zwei Bauarten statt einer:
 *  - **kompakt** (Handy hoch, unter 600 dp): fuer Rail + Raster + Vorschau nebeneinander ist kein
 *    Platz — drei Spalten auf 360 dp ergeben Briefmarken. Deshalb ein Drill-down, wie jede
 *    Streaming-App auf dem Handy: Ebene 1 zeigt nur die Kategorien, ein Tipp fuehrt auf Ebene 2
 *    zum Inhalt, Zurueck fuehrt wieder heraus.
 *  - **mittel/weit** (Handy quer, Tablet): hier passt die Rail neben den Inhalt, nur schmaler als
 *    am Fernseher (der rechnet mit 272 dp aus zwei Metern Abstand). Kein Drill-down — auf einem
 *    Tablet ist das Hin und Her laestig, wenn beides gleichzeitig sichtbar sein koennte.
 *
 * Der Fernseher kommt hier nie an: die Screens rufen den Rahmen nur bei `formfaktor.mobil` auf.
 *
 * [kategorieOffen] lebt bewusst beim Aufrufer (`rememberSaveable`) und nicht hier: der Screen muss
 * ihn auch selbst setzen koennen (Favoriten/Verlauf aus "Mehr" starten direkt auf Ebene 2), und er
 * soll eine Drehung ueberleben.
 *
 * Ist [kategorien] leer, faellt die Kopfzeile weg — das ist der angepinnte Fall aus "Mehr", wo es
 * keine Kategorien gibt, aus denen man kaeme, und der Zurueck-Pfeil ins Leere zeigen wuerde.
 */
@Composable
fun German4kMobilRahmen(
    kategorien: List<RailCategory>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onLongSelect: ((Int) -> Unit)? = null,
    kategorieOffen: Boolean,
    onKategorieOffen: (Boolean) -> Unit,
    titel: String,
    modifier: Modifier = Modifier,
    inhalt: @Composable () -> Unit,
) {
    val formfaktor = LocalFormfaktor.current
    val colors = OwnTVTheme.colors
    val ohneKategorien = kategorien.isEmpty()

    if (formfaktor.kompakt) {
        // Zurueck schliesst erst die Kategorie, dann erst verlaesst es den Bereich. Overlays
        // (Detailseite, Personenseite) melden ihren BackHandler spaeter in der Komposition an und
        // gewinnen deshalb — ihr Zurueck fuehrt wie gewohnt ins Raster, nicht in die Kategorieliste.
        // Das haengt daran, dass die Screens `German4kDetailScreen` NACH dem Rahmen aufrufen: wer
        // das Overlay einmal vor den Rahmen zieht, bekommt Zurueck aus der Detailseite in die
        // Kategorieliste statt ins Raster.
        BackHandler(enabled = kategorieOffen && !ohneKategorien) { onKategorieOffen(false) }

        if (!kategorieOffen && !ohneKategorien) {
            // Ebene 1: die Kategorien als Vollbildliste.
            Column(modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.g4k_mobil_kategorien),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onSurface,
                    modifier = Modifier.padding(16.dp),
                )
                // Rueckkehr aus einer Kategorie soll dort landen, wo man sie verlassen hat — bei
                // ueber hundert Ordnern ist ein Sprung an den Listenanfang jedes Mal aergerlich.
                val listenZustand = rememberLazyListState()
                // German4K: Suche wie in der Fernseher-Rail — bei vielen Ordnern (Laender, Anbieter)
                // ist Scrollen am Handy muehsam. Gefiltert wird nach Namen; jede Zeile behaelt ihren
                // ORIGINAL-Index, damit Auswahl und onSelect weiter stimmen.
                var suche by remember { mutableStateOf("") }
                val sichtbar = remember(kategorien, suche) {
                    val q = suche.trim()
                    if (q.isEmpty()) kategorien.indices.toList()
                    else kategorien.indices.filter { kategorien[it].fullName.contains(q, ignoreCase = true) }
                }
                if (kategorien.size > 12) {
                    SearchBar(
                        query = suche,
                        onQueryChange = { suche = it },
                        placeholder = stringResource(R.string.content_search_categories),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    )
                }
                // German4K: Neuer Suchbegriff → an den Anfang, sonst steht der erste Treffer halb
                // unter dem Suchfeld (die Liste behielte die Scrollposition der vollen Liste).
                LaunchedEffect(suche) {
                    if (suche.isNotEmpty()) runCatching { listenZustand.scrollToItem(0) }
                }
                LaunchedEffect(selectedIndex, kategorien.size) {
                    if (suche.isEmpty() && selectedIndex in kategorien.indices) {
                        runCatching { listenZustand.scrollToItem(selectedIndex) }
                    }
                }
                LazyColumn(state = listenZustand, modifier = Modifier.fillMaxSize()) {
                    if (sichtbar.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.content_no_categories_match),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(count = sichtbar.size, key = { sichtbar[it] }) { pos ->
                        val i = sichtbar[pos]
                        val k = kategorien[i]
                        // German4K: Hier bewusst KEIN requestFocus wie in der unteren Leiste — der
                        // Tipp ersetzt die Liste im selben Zug durch Ebene 2, die angeforderte Zeile
                        // ist also schon weg, bevor der Fokus ankaeme.
                        FocusableSurface(
                            onClick = { onSelect(i); onKategorieOffen(true) },
                            onLongClick = onLongSelect?.let { { it(i) } },
                            selected = i == selectedIndex,
                            shape = RoundedCornerShape(12.dp),
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(German4kMobilZeilenHoehe)
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                        ) { _ ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                k.icon?.let {
                                    OwnTVIcon(icon = it, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(
                                    text = k.labelRes?.let { stringResource(it) } ?: k.fullName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(8.dp))
                                // Chevron nach rechts: das uebliche Zeichen dafuer, dass hinter der
                                // Zeile eine weitere Ebene liegt.
                                OwnTVIcon(
                                    icon = OwnTVIcon.CHEVRON,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Ebene 2: Kopfzeile mit Zurueck-Pfeil + Kategoriename, darunter der Inhalt des Screens.
            Column(modifier.fillMaxSize()) {
                if (!ohneKategorien) {
                    German4kMobilKopfzeile(titel = titel, onZurueck = { onKategorieOffen(false) })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { inhalt() }
            }
        }
    } else {
        // Handy quer / Tablet: Rail neben dem Inhalt. 220 statt 272 dp, weil die Namen aus 40 cm
        // Abstand gelesen werden; auf einem grossen Tablet darf sie wieder etwas breiter sein.
        val railBreite = if (formfaktor.klasse == Breitenklasse.WEIT) 280.dp else 220.dp
        Row(modifier.fillMaxSize()) {
            if (!ohneKategorien) {
                CategoryRail(
                    categories = kategorien,
                    selectedIndex = selectedIndex,
                    onSelect = onSelect,
                    onLongSelect = onLongSelect,
                    width = railBreite,
                    showPanel = false,
                )
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f).fillMaxSize()) { inhalt() }
        }
    }
}
