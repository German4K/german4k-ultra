package tv.own.owntv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.grid.items
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
import tv.own.owntv.ui.theme.OwnTVTheme

// German4K: Personenseite — weitere Titel im Katalog (Kundenwunsch 21.09.2026, Anschluss an Task 4).
// Bewusst ein zweites, schlankes Overlay statt eines dritten Tabs: Name oben, darunter Filme/Serien
// im selben Raster wie die Uebersicht, damit ein Klick auf die Besetzung sich nicht wie ein neuer
// Bildschirm anfuehlt.

private val SeitenRand = 48.dp
private const val SPALTEN = 6

/**
 * Die Personenseite. [titel] == null heisst: der Server antwortet noch, [name] steht aber schon
 * fest (aus der Besetzungszeile der Detailseite) — deshalb ein eigener Name-Parameter statt auf
 * [German4kPersonTitel.name] zu warten.
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
) {
    val colors = OwnTVTheme.colors
    val ersteKachel = remember { FocusRequester() }

    BackHandler { onExit() }

    LaunchedEffect(titel) {
        if (titel != null && (titel.filme.isNotEmpty() || titel.serien.isNotEmpty())) {
            fokusMitWiederholung(ersteKachel)
        }
    }

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
                .padding(horizontal = SeitenRand, vertical = 32.dp),
        ) {
            Text(name, style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
            Spacer(Modifier.height(24.dp))

            when {
                titel == null -> Text(
                    stringResource(R.string.g4k_person_laedt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
                titel.filme.isEmpty() && titel.serien.isEmpty() -> Text(
                    stringResource(R.string.g4k_person_leer),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
                else -> {
                    var kachelIndex = 0
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(SPALTEN),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (titel.filme.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AbschnittsUeberschrift(stringResource(R.string.g4k_person_filme))
                            }
                            items(titel.filme, key = { "film_${it.id}" }) { film ->
                                val fokus = kachelIndex++ == 0
                                PosterCard(
                                    posterUrl = film.posterUrl,
                                    title = film.name,
                                    rating = film.rating,
                                    isFavorite = favoritenFilme.contains(film.id),
                                    modifier = if (fokus) Modifier.focusRequester(ersteKachel) else Modifier,
                                    onClick = { onFilm(film) },
                                )
                            }
                        }
                        if (titel.serien.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                AbschnittsUeberschrift(stringResource(R.string.g4k_person_serien))
                            }
                            items(titel.serien, key = { "serie_${it.id}" }) { serie ->
                                val fokus = kachelIndex++ == 0
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
