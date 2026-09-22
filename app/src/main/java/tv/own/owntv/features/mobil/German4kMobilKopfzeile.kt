package tv.own.owntv.features.mobil

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.ui.components.FocusableSurface
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.theme.OwnTVTheme

/** Hoehe der Kopfzeile und der Kategoriezeilen — Daumengroesse, nicht Fernbedienungsgroesse. */
internal val German4kMobilZeilenHoehe = 56.dp

/**
 * German4K: die Kopfzeile "← Titel" ueber der zweiten Ebene eines mobilen Drill-downs.
 *
 * Eine Stelle fuer alle: der Mobil-Rahmen (Filme/Serien/Live) und die Einstellungen im Hochformat
 * zeigen dieselbe Zeile. Der Fernseher kommt hier nie an — beide Aufrufer stehen hinter
 * `formfaktor.kompakt`.
 */
@Composable
fun German4kMobilKopfzeile(
    titel: String,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val zurueck = stringResource(R.string.g4k_mobil_zurueck)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(German4kMobilZeilenHoehe)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusableSurface(
            onClick = onZurueck,
            shape = CircleShape,
            // OwnTVIcon zeichnet auf Canvas und kennt keine Beschreibung — die Vorlesehilfe haengt
            // deshalb an der Flaeche, die man antippt.
            modifier = Modifier.size(44.dp).semantics { contentDescription = zurueck },
        ) { _ ->
            OwnTVIcon(
                icon = OwnTVIcon.BACK,
                tint = colors.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = titel,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
