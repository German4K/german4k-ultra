package tv.own.owntv.features.mobil

import tv.own.owntv.core.database.entity.EpisodeEntity

/** Trennzeichen, mit denen Anbieter ihre Praefixe an den Folgennamen kleben. */
private fun Char.istTrenner(): Boolean = isWhitespace() || this == '-' || this == '–' || this == '—'

/** "S01E01", "s1e1" und Verwandte am Anfang eines Folgennamens. */
private val StaffelFolgePraefix = Regex("^[Ss]\\d{1,3}[Ee]\\d{1,4}")

/**
 * German4K: raeumt den Folgennamen fuer die schmale Anzeige auf.
 *
 * Anbieter stellen dem Namen dreierlei voran: den ganzen Serientitel, ein blankes Trennzeichen und
 * die Staffel-Folge-Kennung. Auf 360 dp bleibt davon nur die Ellipse — und alle drei stehen auf dem
 * Bildschirm schon woanders: die Serie in der Kopfzeile, die Staffel auf dem Reiter, die Nummer in
 * der Plakette links neben der Zeile.
 *
 * Bleibt nach einem Schnitt nichts Brauchbares uebrig, bleibt der Name so, wie er war — lieber
 * abgeschnitten als leer.
 *
 * Steht hier und nicht mehr in `SeriesScreen`, weil die Funktion reine Rechnerei ist: so kann
 * `German4kFolgentitelTest` sie ohne Compose und ohne Geraet pruefen.
 */
internal fun EpisodeEntity.ohneSerienPraefix(serienName: String): EpisodeEntity {
    var rest = name
    if (serienName.isNotBlank() && rest.startsWith(serienName, ignoreCase = true)) {
        rest = rest.drop(serienName.length)
    }
    rest = rest.dropWhile { it.istTrenner() }
    rest = StaffelFolgePraefix.find(rest)?.let { rest.drop(it.value.length).dropWhile { c -> c.istTrenner() } } ?: rest
    return if (rest.isBlank()) this else copy(name = rest)
}
