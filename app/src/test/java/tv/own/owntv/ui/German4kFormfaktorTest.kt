package tv.own.owntv.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.settings.G4kDarstellung

class German4kFormfaktorTest {
    @Test fun klassenGrenzen() {
        assertEquals(Breitenklasse.KOMPAKT, klasseFuer(411))
        assertEquals(Breitenklasse.KOMPAKT, klasseFuer(599))
        assertEquals(Breitenklasse.MITTEL, klasseFuer(600))
        assertEquals(Breitenklasse.MITTEL, klasseFuer(839))
        assertEquals(Breitenklasse.WEIT, klasseFuer(840))
        assertEquals(Breitenklasse.WEIT, klasseFuer(960))
    }
    @Test fun fernseherIstNieMobil() {
        assertEquals(false, formfaktorFuer(tv = true, darstellung = G4kDarstellung.AUTO, breiteDp = 411).mobil)
        assertEquals(true, formfaktorFuer(tv = true, darstellung = G4kDarstellung.MOBIL, breiteDp = 411).mobil)   // Schalter gewinnt
    }
    @Test fun handyIstMobilAusserSchalterSagtTv() {
        assertEquals(true, formfaktorFuer(false, G4kDarstellung.AUTO, 411).mobil)
        assertEquals(false, formfaktorFuer(false, G4kDarstellung.TV, 411).mobil)
        assertEquals(true, formfaktorFuer(false, G4kDarstellung.AUTO, 411).kompakt)
        assertEquals(false, formfaktorFuer(false, G4kDarstellung.AUTO, 731).kompakt)   // Handy quer = MITTEL
    }
}
