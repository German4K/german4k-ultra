package tv.own.owntv.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.german4k.German4kPanelAnswer
import tv.own.owntv.core.german4k.German4kProvisioner
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.ui.components.OwnTVButton
import tv.own.owntv.ui.components.OwnTVButtonStyle
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.components.OwnTVSpinner
import tv.own.owntv.ui.components.OwnTVTextField
import tv.own.owntv.ui.components.detailText
import tv.own.owntv.ui.components.primaryText
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * German4K Ultra first start: the panel provisions this device (zero setup). Replaces OwnTV's
 * onboarding wizard while the device is paired; [onManual] hands over to the wizard for anyone who
 * insists on adding a foreign source by hand.
 */
@Composable
fun German4kSetup(onReady: (Long?) -> Unit, onManual: () -> Unit, modifier: Modifier = Modifier) {
    val provisioner: German4kProvisioner = koinInject()
    val state by provisioner.state.collectAsStateWithLifecycle()
    var loginOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { if (provisioner.state.value is German4kProvisioner.State.Idle) provisioner.provision() }
    LaunchedEffect(state) {
        val s = state
        if (s is German4kProvisioner.State.Ready) onReady(s.profileId)
    }

    Box(modifier = modifier.fillMaxSize().background(OwnTVTheme.colors.background)) {
        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).widthIn(max = 980.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (val s = state) {
                    German4kProvisioner.State.Idle, German4kProvisioner.State.Checking -> Waiting(stringResource(R.string.g4k_checking))
                    is German4kProvisioner.State.Importing -> ImportingView(s)
                    is German4kProvisioner.State.Ready -> Waiting(stringResource(R.string.g4k_importing))
                    is German4kProvisioner.State.Uncoupled -> if (loginOpen) {
                        LoginView(s.answer, onLogin = { u, p -> loginOpen = false; provisioner.login(u, p) }, onBack = { loginOpen = false })
                    } else {
                        UncoupledView(s.answer, s.loginFailed, onLogin = { loginOpen = true }, onRetry = provisioner::provision, onManual = onManual)
                    }
                    is German4kProvisioner.State.Locked -> Problem(s.answer.noteTitle, s.answer.noteContent, onRetry = provisioner::provision, onManual = null)
                    German4kProvisioner.State.Offline -> Problem(stringResource(R.string.g4k_offline_title), stringResource(R.string.g4k_offline_body), onRetry = provisioner::provision, onManual = onManual)
                    is German4kProvisioner.State.Failed -> Problem(stringResource(R.string.g4k_failed_title), s.message, onRetry = provisioner::provision, onManual = onManual)
                }
            }
        }
    }
}

@Composable
private fun Waiting(text: String) {
    val colors = OwnTVTheme.colors
    OwnTVSpinner(sizeDp = 56)
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
}

@Composable
private fun ImportingView(s: German4kProvisioner.State.Importing) {
    val colors = OwnTVTheme.colors
    val progress by s.importer.progress.collectAsStateWithLifecycle()
    val display = progress?.importProgressDisplay()
    OwnTVSpinner(sizeDp = 56)
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.g4k_importing), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
    Spacer(Modifier.height(8.dp))
    display?.let { Text(it.primaryText(), style = MaterialTheme.typography.headlineLarge, color = colors.primary) }
    Spacer(Modifier.height(6.dp))
    display?.let { Text(it.detailText(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
}

@Composable
private fun UncoupledView(answer: German4kPanelAnswer, loginFailed: Boolean, onLogin: () -> Unit, onRetry: () -> Unit, onManual: () -> Unit) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    val qr = remember(answer.einrichtenUrl) { answer.einrichtenUrl.takeIf { it.isNotBlank() }?.let { CompanionLink.renderQr(it, 400) } }

    Text(stringResource(R.string.g4k_uncoupled_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.g4k_uncoupled_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    if (loginFailed && answer.noteContent.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(answer.noteContent, style = MaterialTheme.typography.bodyMedium, color = colors.primary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 720.dp))
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(300.dp)) {
            Way(stringResource(R.string.g4k_way_login_title), stringResource(R.string.g4k_way_login_body))
            Spacer(Modifier.height(10.dp))
            OwnTVButton(stringResource(R.string.g4k_login_button), onClick = onLogin, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
            Spacer(Modifier.height(22.dp))
            Way(stringResource(R.string.g4k_way_photo_title), stringResource(R.string.g4k_way_photo_body))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.g4k_contact), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Column(Modifier.width(300.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Way(stringResource(R.string.g4k_way_qr_title), stringResource(R.string.g4k_way_qr_body))
            Spacer(Modifier.height(10.dp))
            qr?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(172.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(8.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(stringResource(R.string.g4k_einrichten_short), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.g4k_mac), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(answer.mac.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.primary, letterSpacing = 2.sp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.g4k_device_code), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Text(answer.deviceKey, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.primary, letterSpacing = 4.sp)
        }
    }
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OwnTVButton(stringResource(R.string.g4k_retry_button), onClick = onRetry, style = OwnTVButtonStyle.SECONDARY)
        OwnTVButton(stringResource(R.string.g4k_manual_button), onClick = onManual, style = OwnTVButtonStyle.SECONDARY)
    }
}

@Composable
private fun Way(title: String, body: String) {
    val colors = OwnTVTheme.colors
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.onSurface)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
}

@Composable
private fun LoginView(answer: German4kPanelAnswer, onLogin: (String, String) -> Unit, onBack: () -> Unit) {
    val colors = OwnTVTheme.colors
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    BackHandler { onBack() }
    Text(stringResource(R.string.g4k_login_title), style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.g4k_way_login_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    // OwnTV's pill text field is built for D-pad focus; on a touch device (tablet, phone) a tap does not
    // move the IME into the second field. Touch devices get plain fields instead.
    val context = LocalContext.current
    val isTv = remember(context) {
        (context.getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    Column(Modifier.width(460.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isTv) {
            OwnTVTextField(user, { user = it }, label = stringResource(R.string.g4k_username), modifier = Modifier.fillMaxWidth(), focusRequester = first)
            OwnTVTextField(pass, { pass = it }, label = stringResource(R.string.g4k_password), modifier = Modifier.fillMaxWidth(), keyboardType = KeyboardType.Password)
        } else {
            TouchField(user, { user = it }, label = stringResource(R.string.g4k_username), keyboardType = KeyboardType.Text)
            TouchField(pass, { pass = it }, label = stringResource(R.string.g4k_password), keyboardType = KeyboardType.Password, password = true)
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OwnTVButton(stringResource(R.string.g4k_back), onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
        OwnTVButton(stringResource(R.string.g4k_login_go), onClick = { onLogin(user.trim(), pass) }, icon = OwnTVIcon.PLAY, enabled = user.isNotBlank() && pass.isNotBlank())
    }
    Spacer(Modifier.height(10.dp))
    Text(answer.loginHost, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
}

@Composable
private fun Problem(title: String, body: String, onRetry: () -> Unit, onManual: (() -> Unit)?) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Text(title, style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 620.dp))
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.g4k_contact), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OwnTVButton(stringResource(R.string.g4k_retry_button), onClick = onRetry, modifier = Modifier.focusRequester(fr))
        if (onManual != null) OwnTVButton(stringResource(R.string.g4k_manual_button), onClick = onManual, style = OwnTVButtonStyle.SECONDARY)
    }
}

@Composable
private fun TouchField(value: String, onValueChange: (String) -> Unit, label: String, keyboardType: KeyboardType, password: Boolean = false) {
    val colors = OwnTVTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.onSurface, fontSize = 18.sp),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.primary, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}
