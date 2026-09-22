package tv.own.owntv.features.mobil

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.EpisodeEntity

/**
 * German4K: Der Folgentitel-Helfer ist reine Rechnerei — er gehoert unter Test, nicht unter das
 * Auge auf einem 360-dp-Bildschirm. Geprueft wird jede Schnittstelle, die Anbieter uns liefern:
 * Serientitel voran, Gross-/Kleinschreibung egal, Gedankenstrich als Trenner, Staffel-Folge-Kennung.
 */
class German4kFolgentitelTest {

    private fun folge(name: String) = EpisodeEntity(
        seriesId = 1L,
        seasonNumber = 1,
        episodeNumber = 1,
        name = name,
        streamUrl = "http://example.invalid/1",
    )

    @Test
    fun serienPraefixMitBindestrichFaelltWeg() {
        assertEquals(
            "Stilles Wasser",
            folge("Tatort - Stilles Wasser").ohneSerienPraefix("Tatort").name,
        )
    }

    @Test
    fun serienPraefixIgnoriertGrossKleinschreibung() {
        assertEquals(
            "Stilles Wasser",
            folge("TATORT - Stilles Wasser").ohneSerienPraefix("Tatort").name,
        )
    }

    @Test
    fun gedankenstricheZaehlenAlsTrenner() {
        assertEquals(
            "Stilles Wasser",
            folge("Tatort – Stilles Wasser").ohneSerienPraefix("Tatort").name,
        )
        assertEquals(
            "Stilles Wasser",
            folge("Tatort — Stilles Wasser").ohneSerienPraefix("Tatort").name,
        )
    }

    @Test
    fun staffelFolgeKennungFaelltWeg() {
        assertEquals(
            "Stilles Wasser",
            folge("Tatort - S01E01 - Stilles Wasser").ohneSerienPraefix("Tatort").name,
        )
        assertEquals(
            "Stilles Wasser",
            folge("Tatort - s1e1 - Stilles Wasser").ohneSerienPraefix("Tatort").name,
        )
    }

    @Test
    fun titelOhnePraefixBleibtWieErIst() {
        assertEquals(
            "Stilles Wasser",
            folge("Stilles Wasser").ohneSerienPraefix("Tatort").name,
        )
    }

    @Test
    fun bleibtNichtsUebrigBleibtDerNameStehen() {
        // Der ganze Name IST der Serientitel plus Kennung — abgeschnitten waere die Zeile leer.
        assertEquals(
            "Tatort - S01E01",
            folge("Tatort - S01E01").ohneSerienPraefix("Tatort").name,
        )
    }

    @Test
    fun leererSerienNameSchneidetNichts() {
        assertEquals(
            "Tatort - Stilles Wasser",
            folge("Tatort - Stilles Wasser").ohneSerienPraefix("").name,
        )
    }
}
