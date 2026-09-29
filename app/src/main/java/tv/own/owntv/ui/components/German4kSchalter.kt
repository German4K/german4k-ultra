package tv.own.owntv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * German4K 3.0/32 (O2): ein echter Schalter statt „Ein"/„Aus" als Text.
 *
 * Nur Anzeige — die Zeile, in der er steht, ist das Bedienelement (OK schaltet um). Ein eigener
 * fokussierbarer Schalter daneben wäre am Fernseher ein zweiter Halt ohne eigene Aufgabe.
 */
@Composable
fun German4kSchalter(an: Boolean, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val shape = RoundedCornerShape(50)
    val knopf by animateDpAsState(if (an) 18.dp else 0.dp, label = "g4kSchalter")
    Box(
        modifier = modifier
            .size(width = 40.dp, height = 22.dp)
            .clip(shape)
            .background(if (an) colors.primary else colors.surfaceContainerHighest)
            .border(1.dp, if (an) colors.primary else colors.outlineVariant, shape)
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knopf)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (an) colors.onPrimary else Color.White.copy(alpha = 0.85f)),
        )
    }
}
