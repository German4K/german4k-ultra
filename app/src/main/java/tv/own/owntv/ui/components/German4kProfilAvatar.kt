package tv.own.owntv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.BasicText
import coil3.compose.AsyncImage
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * German4K 3.0 (32), Entscheidung Betreiber 29.09.2026 (C2): das Profilbild ist ein Kreis mit dem
 * Anfangsbuchstaben des Profilnamens in Weiss — lila Verlauf (primary → primaryContainer). Ein
 * Kinderprofil bekommt einen gruenen Kreis mit Kind-Symbol. Ersetzt die gezeichneten OwnTV-Kacheln
 * (oranger Blitz) in der Seitenleiste und auf "Wer schaut gerade?".
 *
 * Ein selbst gewaehltes Foto ([imagePath]) bleibt vorrangig — wer eins gesetzt hat, will es sehen.
 */
@Composable
fun German4kProfilAvatar(
    name: String,
    isKids: Boolean,
    modifier: Modifier = Modifier,
    imagePath: String? = null,
) {
    val colors = OwnTVTheme.colors
    val brush = if (isKids) {
        Brush.linearGradient(listOf(KidsGreenTop, KidsGreenBottom))
    } else {
        Brush.linearGradient(listOf(colors.primary, colors.primaryContainer))
    }
    BoxWithConstraints(
        modifier = modifier.clip(CircleShape).background(brush, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val kante = if (maxWidth < maxHeight) maxWidth else maxHeight
        val buchstabe = name.trim().firstOrNull()?.uppercaseChar()
        when {
            isKids -> Image(
                imageVector = Icons.Rounded.ChildCare,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(kante * 0.58f),
            )
            buchstabe != null -> BasicText(
                text = buchstabe.toString(),
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = with(LocalDensity.current) { (kante * 0.46f).toSp() },
                ),
                maxLines = 1,
            )
            else -> Image(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                colorFilter = ColorFilter.tint(Color.White),
                modifier = Modifier.size(kante * 0.58f),
            )
        }
        if (!imagePath.isNullOrBlank()) {
            AsyncImage(
                model = java.io.File(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

private val KidsGreenTop = Color(0xFF34D399)
private val KidsGreenBottom = Color(0xFF15803D)
