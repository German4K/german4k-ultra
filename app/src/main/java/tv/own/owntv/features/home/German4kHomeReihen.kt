package tv.own.owntv.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.german4k.German4kSpiel
import tv.own.owntv.core.home.HeroItem
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.features.sport.SpielKarte
import tv.own.owntv.features.sport.SportLiveRot
import tv.own.owntv.ui.LocalFormfaktor
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.PosterCard
import tv.own.owntv.ui.format.rememberSystemTimeFormatter
import tv.own.owntv.ui.theme.Dimens
import tv.own.owntv.ui.theme.OwnTVTheme

// German4K 3.0/32 (D2/E2): die eigenen Startseiten-Reihen — Fußball, Live-Weiterschauen, neue Filme.

/** Reihenkopf im Stil der übrigen Startseiten-Reihen (Großbuchstaben, Akzentfarbe). */
@Composable
private fun ReihenTitel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall,
        color = OwnTVTheme.colors.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = Dimens.HomeRowPaddingH),
    )
    Spacer(Modifier.height(10.dp))
}

private fun Modifier.ersteKarte(index: Int, fokus: FocusRequester?): Modifier =
    if (index == 0 && fokus != null) focusRequester(fokus) else this

/** „Jetzt im Fußball": dieselben Karten wie im Bereich Fußball, höchstens acht. */
@Composable
internal fun German4kFussballReihe(
    spiele: List<German4kSpiel>,
    onKlick: (German4kSpiel) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
) {
    val mobil = LocalFormfaktor.current.mobil
    Column(Modifier.fillMaxWidth()) {
        ReihenTitel(stringResource(R.string.g4k_home_fussball))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = Dimens.HomeRowPaddingH, vertical = 4.dp),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(spiele, key = { _, s -> s.id }) { index, s ->
                SpielKarte(
                    spiel = s,
                    onClick = { onKlick(s) },
                    modifier = Modifier
                        .width(if (mobil) 250.dp else 280.dp)
                        .height(if (mobil) 128.dp else 140.dp)
                        .ersteKarte(index, firstItemFocusRequester)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
            }
        }
    }
}

/** „Neu bei Filme": Plakate der neuesten Filme, Klick öffnet die Detailseite. */
@Composable
internal fun German4kNeuFilmeReihe(
    filme: List<MovieEntity>,
    onKlick: (MovieEntity) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
) {
    Column(Modifier.fillMaxWidth()) {
        ReihenTitel(stringResource(R.string.g4k_home_neu_filme))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = Dimens.HomeRowPaddingH),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(filme, key = { _, m -> m.id }) { index, m ->
                Box(Modifier.width(150.dp)) {
                    PosterCard(
                        posterUrl = m.posterUrl,
                        title = m.name,
                        rating = m.rating,
                        modifier = Modifier.ersteKarte(index, firstItemFocusRequester),
                        onFocus = onFocus,
                        onClick = { onKlick(m) },
                    )
                }
            }
        }
    }
}

/**
 * „Weiterschauen" für Sender: Querformat-Karte mit Logo links auf dunkler Kachel, rotem LIVE-Chip,
 * darunter die laufende Sendung aus dem gespeicherten Guide mit Uhrzeit und Fortschritt.
 */
@Composable
internal fun German4kLiveWeiterReihe(
    items: List<HeroItem.LiveHero>,
    jetzt: Map<Long, EpgProgrammeEntity>,
    onPlay: (HeroItem.LiveHero) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester?,
) {
    val mobil = LocalFormfaktor.current.mobil
    val formatTime = rememberSystemTimeFormatter()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); nowMs = System.currentTimeMillis() } }
    Column(Modifier.fillMaxWidth()) {
        ReihenTitel(stringResource(R.string.home_keep_watching))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = Dimens.HomeRowPaddingH, vertical = 4.dp),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(items, key = { _, it -> it.channel.id }) { index, item ->
                val prog = jetzt[item.channel.id]?.takeIf { nowMs < it.stopMs }
                LiveKarte(
                    item = item,
                    prog = prog,
                    zeit = prog?.let { stringResource(R.string.g4k_zeitspanne, formatTime(it.startMs), formatTime(it.stopMs)) },
                    fortschritt = prog?.let { p ->
                        val dauer = (p.stopMs - p.startMs).coerceAtLeast(1L)
                        ((nowMs - p.startMs).toFloat() / dauer).coerceIn(0f, 1f)
                    },
                    onClick = { onPlay(item) },
                    modifier = Modifier
                        .width(if (mobil) 240.dp else 280.dp)
                        .ersteKarte(index, firstItemFocusRequester)
                        .onFocusChanged { if (it.hasFocus) onFocus() },
                )
            }
        }
    }
}

@Composable
private fun LiveKarte(
    item: HeroItem.LiveHero,
    prog: EpgProgrammeEntity?,
    zeit: String?,
    fortschritt: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        surface = GlassSurface.CARDS,
        focusedScale = 1.03f,
        glowElevation = 8,
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.TopStart,
    ) { focused ->
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LiveKachelDunkel),
            ) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.fillMaxHeight().aspectRatio(1f).background(LiveKachelLogo),
                        contentAlignment = Alignment.Center,
                    ) {
                        val logo = item.channel.logoUrl
                        if (!logo.isNullOrBlank()) {
                            AsyncImage(
                                model = logo,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(14.dp),
                            )
                        } else {
                            OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().padding(10.dp), contentAlignment = Alignment.CenterStart) {
                        Text(
                            item.channel.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    stringResource(R.string.g4k_live_kurz),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SportLiveRot)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                prog?.title ?: item.channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (zeit != null) {
                Text(
                    zeit,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(100))
                    .background(Color.Black.copy(alpha = 0.25f)),
            ) {
                if (fortschritt != null) {
                    Box(Modifier.fillMaxWidth(fortschritt).height(4.dp).background(colors.primary))
                }
            }
        }
    }
}

private val LiveKachelDunkel = Color(0xFF15101F)
private val LiveKachelLogo = Color(0xFF221A33)
