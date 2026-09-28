package tv.own.owntv.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.settings.SettingsRepository.SortMode
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * Compact sort toggle shown next to a section's search bar. The label names the *current* mode.
 *
 * German4K: Klick schaltete frueher eine Stufe weiter (Wiedergabeliste → A–Z → Bewertung →
 * Hinzugefuegt am → …). Wer nach Bewertung sortieren wollte, musste raten, wie oft er druecken
 * muss, und sah zwischendurch jedes Mal eine falsch sortierte Liste (Rueckmeldung Aleks959,
 * 23.09.2026). Jetzt oeffnet der Klick ein Auswahlfenster — man sieht alle Moeglichkeiten und
 * trifft in einem Schritt. [modi] bestimmt, was drinsteht: Live/EPG koennen nur
 * Wiedergabeliste und A–Z, Bewertung und Hinzugefuegt am gibt es nur bei Filmen und Serien.
 */
@Composable
fun SortChip(
    mode: SortMode,
    onSelect: (SortMode) -> Unit,
    modifier: Modifier = Modifier,
    playlistLabel: String? = null,
    // German4K: YEAR = Erscheinungsjahr (Kundenwunsch Aleks959).
    modi: List<SortMode> = listOf(SortMode.PLAYLIST, SortMode.ALPHA, SortMode.RATING, SortMode.DATE_ADDED, SortMode.YEAR),
) {
    val colors = OwnTVTheme.colors
    val resolvedPlaylistLabel = playlistLabel ?: stringResource(R.string.settings_sort_playlist)
    val label: @Composable (SortMode) -> String = {
        when (it) {
            SortMode.PLAYLIST -> resolvedPlaylistLabel
            SortMode.ALPHA -> stringResource(R.string.settings_sort_alpha)
            // Im Menue heisst es „Bewertung (IMDb)" — im Chip bleibt der kurze OwnTV-Text, sonst
            // sprengt er die Zeile neben dem Suchfeld.
            SortMode.RATING -> stringResource(R.string.settings_sort_rating)
            SortMode.DATE_ADDED -> stringResource(R.string.settings_sort_date_added)
            SortMode.YEAR -> stringResource(R.string.g4k_sort_jahr) // German4K: Aleks959
        }
    }
    var offen by remember { mutableStateOf(false) }
    // Ohne das landet der Fokus nach dem Schliessen irgendwo in der Kachelwand, und das D-Pad
    // springt nicht mehr dahin zurueck, wo der Kunde gerade war.
    val chipFokus = remember { FocusRequester() }
    FocusableSurface(
        onClick = { offen = true },
        // Same height + pill shape as SearchBar so the two read as one row of controls.
        modifier = modifier.height(48.dp).focusRequester(chipFokus),
        shape = RoundedCornerShape(50),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
        surface = GlassSurface.CARDS,
    ) { focused ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnTVIcon(
                OwnTVIcon.SORT,
                tint = if (focused) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label(mode),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (focused) colors.primary else colors.onSurface,
            )
        }
    }

    if (offen) {
        val schliessen = { offen = false; runCatching { chipFokus.requestFocus() }; Unit }
        PickerDialog(
            title = stringResource(R.string.g4k_sort_titel),
            options = modi.map { m ->
                m.name to if (m == SortMode.RATING) stringResource(R.string.g4k_sort_imdb) else label(m)
            },
            selected = mode.name,
            onSelect = { gewaehlt ->
                runCatching { SortMode.valueOf(gewaehlt) }.getOrNull()?.let(onSelect)
                schliessen()
            },
            onDismiss = schliessen,
        )
    }
}
