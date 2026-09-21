package tv.own.owntv.ui.components

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

// German4K: geteilter Fokus-Helfer fuer die German4K-Overlays. Ein einzelner `requestFocus()` im
// selben Frame wie die erste Komposition geht ins Leere — siehe die Diagnose in FocusTrap.kt
// (restoreAfterDialogClose) und German4kDetailScreen.kt. Statt jede Seite den Versuch neu bauen zu
// lassen, gibt es hier einen Ort dafuer.

/** Fordert den Fokus an, bis es klappt — ein einzelner Versuch beim Aufgehen der Seite geht ins Leere (siehe FocusTrap). */
suspend fun fokusMitWiederholung(requester: FocusRequester, versuche: Int = 12): Boolean {
    repeat(versuche) {
        withFrameNanos {}
        if (runCatching { requester.requestFocus() }.isSuccess) return true
    }
    return false
}
