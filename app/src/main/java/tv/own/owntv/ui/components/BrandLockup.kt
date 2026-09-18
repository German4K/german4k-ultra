package tv.own.owntv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.LocalTextStyle
import tv.own.owntv.R
import tv.own.owntv.ui.theme.AccentCyan
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * German4K lockup: the round website logo next to the "German4K" wordmark drawn from brand tokens so it
 * stays legible on both themes. [vertical] stacks logo over wordmark for narrow panes (the preview
 * column); the wordmark never wraps letter by letter.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    textSize: Int = 26,
    vertical: Boolean = false,
) {
    val colors = OwnTVTheme.colors
    val own = stringResource(R.string.brand_own)
    val tv = stringResource(R.string.brand_tv)
    val mark: @Composable () -> Unit = {
        Image(
            painter = painterResource(R.drawable.g4k_logo_rund),
            contentDescription = null,
            modifier = Modifier
                .size(markSize.dp)
                .clip(CircleShape)
                .border(2.dp, AccentCyan, CircleShape),
        )
    }
    val wordmark: @Composable () -> Unit = {
        // Auto-size down to the available width so a narrow pane never clips or wraps the wordmark.
        BasicText(
            text = buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold)) {
                    append(own)
                }
                withStyle(androidx.compose.ui.text.SpanStyle(color = AccentCyan, fontWeight = FontWeight.Bold)) {
                    append(tv)
                }
            },
            style = LocalTextStyle.current.copy(fontSize = textSize.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = textSize.sp, stepSize = 1.sp),
            modifier = if (vertical) Modifier.fillMaxWidth() else Modifier,
        )
    }
    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            mark()
            wordmark()
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            mark()
            wordmark()
        }
    }
}
