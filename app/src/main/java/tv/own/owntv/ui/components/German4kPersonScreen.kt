package tv.own.owntv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.german4k.German4kPersonTitel
import tv.own.owntv.ui.LocalFormfaktor
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

// German4K: Personenseite — weitere Titel im Katalog (Kundenwunsch 21.09.2026, Anschluss an Task 4).
// Bewusst ein zweites, schlankes Overlay statt eines dritten Tabs: Name oben, darunter Filme/Serien
// im selben Raster wie die Uebersicht, damit ein Klick auf die Besetzung sich nicht wie ein neuer
// Bildschirm anfuehlt.

private const val SPALTEN = 6

/**
 * Die Personenseite. [titel] == null und [fehler] == false heisst: der Server antwortet noch,
 * [name] steht aber schon fest (aus der Besetzungszeile der Detailseite) — deshalb ein eigener
 * Name-Parameter statt auf [German4kPersonTitel.name] zu warten.
 *
 * [fehler] ist der dritte Zustand: der Aufruf ist durch, aber ohne Antwort (Server weg, Quelle
 * kein Xtream, kaputtes JSON). Ohne ihn blieb „Laedt…" fuer immer stehen und sah aus wie ein
 * haengender Bildschirm.
 */
@Composable
fun German4kPersonScreen(
    name: String,
    titel: German4kPersonTitel?,
    favoritenFilme: Set<Long>,
    favoritenSerien: Set<Long>,
    onFilm: (MovieEntity) -> Unit,
    onSerie: (SeriesEntity) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    fehler: Boolean = false,
) {
    val colors = OwnTVTheme.colors
    val ersteKachel = remember { FocusRequester() }
    // German4K: Auch „Laedt…"/„Nichts gefunden" braucht ein Fokusziel. Die Seite faengt mit
    // `trapAllFocusExit()` jede Fokusuebergabe nach aussen ab; gab es drinnen kein fokussierbares
    // Element, war das D-Pad tot und nur „Zurueck" kam noch raus.
    val hinweisFokus = remember { FocusRequester() }
    // German4K: Erst wenn es wirklich Kacheln gibt, ist die Seite „voll" — sonst steht hier ein
    // Hinweistext (laedt / nichts gefunden), und der traegt das Fokusziel.
    val kacheln = titel?.takeIf { it.filme.isNotEmpty() || it.serien.isNotEmpty() }

    BackHandler { onExit() }

    LaunchedEffect(titel, fehler) {
        fokusMitWiederholung(if (kacheln != null) ersteKachel else hinweisFokus)
    }

    val formfaktor = LocalFormfaktor.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .modalScrim()
            .trapAllFocusExit()
            .focusGroup(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                // German4K: derselbe schmale Rand wie auf der Detailseite — 48 dp waeren auf einem
                // Handy ein Viertel der Breite.
                .padding(
                    horizontal = if (formfaktor.kompakt) 16.dp else Dimens.DetailSeitenRand,
                    vertical = if (formfaktor.kompakt) 16.dp else 32.dp,
                ),
        ) {
            Text(name, style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            Spacer(Modifier.height(24.dp))

            if (kacheln == null) {
                Text(
                    // German4K: Fehler und „nichts gefunden" sehen fuer den Zuschauer gleich aus —
                    // in beiden Faellen gibt es hier nichts weiter von dieser Person.
                    stringResource(if (titel == null && !fehler) R.string.g4k_person_laedt else R.string.g4k_person_leer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .focusRequester(hinweisFokus)
                        .focusable(),
                )
            } else {
                LazyVerticalGrid(
                    // German4K: sechs feste Spalten ergeben auf 360 dp Briefmarken von 50 dp. Mobil
                    // rechnet das Raster die Spaltenzahl deshalb aus der Breite aus.
                    columns = if (formfaktor.mobil) GridCells.Adaptive(minSize = 150.dp) else GridCells.Fixed(SPALTEN),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (kacheln.filme.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AbschnittsUeberschrift(stringResource(R.string.g4k_person_filme))
                        }
                        // German4K: Das Fokusziel kommt aus den Daten, nicht aus einem mitlaufenden
                        // Zaehler. Compose ruft die Kachel-Bausteine nur fuer sichtbare Zeilen und in
                        // beliebiger Reihenfolge auf — ein `kachelIndex++` im Baustein zaehlte deshalb
                        // mal die erste, mal irgendeine Kachel als „erste".
                        itemsIndexed(kacheln.filme, key = { _, it -> "film_${it.id}" }) { index, film ->
                            PosterCard(
                                posterUrl = film.posterUrl,
                                title = film.name,
                                rating = film.rating,
                                isFavorite = favoritenFilme.contains(film.id),
                                modifier = if (index == 0) Modifier.focusRequester(ersteKachel) else Modifier,
                                onClick = { onFilm(film) },
                            )
                        }
                    }
                    if (kacheln.serien.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AbschnittsUeberschrift(stringResource(R.string.g4k_person_serien))
                        }
                        itemsIndexed(kacheln.serien, key = { _, it -> "serie_${it.id}" }) { index, serie ->
                            // Gibt es keine Filme, ist die erste Serie die erste Kachel der Seite.
                            val fokus = index == 0 && kacheln.filme.isEmpty()
                            PosterCard(
                                posterUrl = serie.posterUrl,
                                title = serie.name,
                                rating = serie.rating,
                                isFavorite = favoritenSerien.contains(serie.id),
                                modifier = if (fokus) Modifier.focusRequester(ersteKachel) else Modifier,
                                onClick = { onSerie(serie) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AbschnittsUeberschrift(text: String) {
    val colors = OwnTVTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp),
    )
}
