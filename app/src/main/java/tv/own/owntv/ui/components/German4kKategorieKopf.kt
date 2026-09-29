package tv.own.owntv.ui.components

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.own.owntv.ui.LocalFormfaktor
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * German4K 3.0/32 (G2): einzeiliger Kopf einer Inhaltsliste — Kategoriename groß, Anzahl gedämpft
 * mit Tausenderpunkt („Alle Filme  72.691"). Auf Handy/Tablet trägt die Kopfzeile des Mobil-Rahmens
 * den Kategorienamen schon, dort steht nur „72.691 Filme".
 *
 * Gemeinsam für Live-TV, Filme und Serien, damit die drei Köpfe nicht auseinanderlaufen.
 */
@Composable
fun German4kKategorieKopf(
    name: String,
    count: Int,
    @PluralsRes mobilAnzahl: Int,
    titelStil: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val colors = OwnTVTheme.colors
    if (!LocalFormfaktor.current.mobil) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                name,
                style = titelStil,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                formatCount(count),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    } else {
        Text(
            pluralStringResource(mobilAnzahl, count, formatCount(count)),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurfaceVariant,
        )
    }
}
