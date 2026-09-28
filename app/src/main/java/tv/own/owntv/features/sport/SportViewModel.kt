package tv.own.owntv.features.sport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.german4k.German4kSpiel
import tv.own.owntv.core.german4k.German4kSportAntwort
import tv.own.owntv.core.german4k.German4kSportRepository
import tv.own.owntv.core.german4k.German4kSportSender
import tv.own.owntv.core.german4k.SPORT_ZONE
import tv.own.owntv.core.german4k.SportChannelResolver
import tv.own.owntv.core.german4k.SportChip
import tv.own.owntv.core.german4k.SportRegale
import tv.own.owntv.core.german4k.chipFilter
import tv.own.owntv.core.german4k.regale
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeSourceIds
import tv.own.owntv.core.settings.SettingsRepository
import java.time.LocalDate

/** Was ein Klick auf eine Spielkarte bewirkt. */
sealed interface SportKlick {
    /** Genau ein eingeschalteter Sender, der in der eigenen Liste steht → sofort abspielen. */
    data class Spielen(val channel: ChannelEntity) : SportKlick
    /** Sonst die Spielseite mit allen Sendern / Rechte / Bereich. */
    data class Oeffnen(val spiel: German4kSpiel) : SportKlick
}

/**
 * German4K 3.0: Bereich „Fußball" (Sport-Hub). Der Spielplan kommt vom Server
 * ([German4kSportRepository]), die App ordnet nur ein und löst streamIds in Kanäle auf.
 * Vor jedem Abspielen wird neu geholt — eine streamId aus dem Speicher kann schon falsch sein.
 */
class SportViewModel(
    private val repo: German4kSportRepository,
    private val resolver: SportChannelResolver,
    private val categoryDao: CategoryDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    val antwort: StateFlow<German4kSportAntwort?> = repo.antwort
    val offline: StateFlow<Boolean> = repo.offline
    val chip = MutableStateFlow(SportChip.ALLE)

    val regale: StateFlow<SportRegale> = combine(repo.antwort, chip) { a, c ->
        if (a == null || !a.ok) SportRegale() else regale(chipFilter(a.spiele, c), LocalDate.now(SPORT_ZONE), SPORT_ZONE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SportRegale())

    private val offenId = MutableStateFlow<Long?>(null)
    private val _offenesSpiel = MutableStateFlow<German4kSpiel?>(null)
    val offenesSpiel: StateFlow<German4kSpiel?> = _offenesSpiel.asStateFlow()

    init {
        // Die offene Spielseite folgt jeder neuen Antwort (Sender kommen kurz vor Anpfiff dazu).
        viewModelScope.launch {
            combine(repo.antwort, offenId) { a, id -> id?.let { i -> a?.spiele?.firstOrNull { it.id == i } } }
                .collect { frisch -> if (frisch != null) _offenesSpiel.value = frisch }
        }
    }

    fun starte() = repo.starteAbfrage()
    fun stoppe() = repo.stoppeAbfrage()

    fun oeffne(spiel: German4kSpiel) { offenId.value = spiel.id; _offenesSpiel.value = spiel }
    fun schliesse() { offenId.value = null; _offenesSpiel.value = null }

    /** Immer erst neu holen, dann entscheiden: direkt spielen oder Spielseite. */
    suspend fun klick(spiel: German4kSpiel): SportKlick {
        val frisch = repo.ladeJetzt() ?: repo.antwort.value
        val s = frisch?.spiele?.firstOrNull { it.id == spiel.id } ?: spiel
        val an = s.sender.filter { it.an == true }
        if (an.size == 1) resolver.aufloesen(an[0].streamIds)?.let { return SportKlick.Spielen(it) }
        return SportKlick.Oeffnen(s)
    }

    /** Für die Vorschau beim Fokus: löst aus dem aktuellen Stand auf (höchstens 60 s alt). */
    suspend fun kanalVorschau(sender: German4kSportSender): ChannelEntity? = resolver.aufloesen(sender.streamIds)

    /** Vor dem Abspielen: neu holen, denselben Sender im frischen Spiel suchen, dessen streamIds auflösen. */
    suspend fun kanalFuer(spielId: Long, sender: German4kSportSender): ChannelEntity? {
        val frisch = repo.ladeJetzt() ?: repo.antwort.value
        val s = frisch?.spiele?.firstOrNull { it.id == spielId }
        val aktuell = s?.sender?.firstOrNull { it.name == sender.name && it.platz == sender.platz } ?: sender
        if (aktuell.an == false) return null
        return resolver.aufloesen(aktuell.streamIds)
    }

    /** Live-Kategorie der aktiven Quellen mit diesem Namen (ohne Groß/Klein), oder null. */
    suspend fun kategorieId(name: String?): Long? {
        val gesucht = name?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val profil = settings.activeProfileIdNow()
        if (profil < 0) return null
        val quellen = activeSourceIds(settings, sourceDao, profil, MediaType.LIVE)
        if (quellen.isEmpty()) return null
        return categoryDao.observe(quellen, MediaType.LIVE).first().firstOrNull { it.name.trim().equals(gesucht, ignoreCase = true) }?.id
    }
}
