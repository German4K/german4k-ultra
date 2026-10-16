package tv.own.owntv.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.backup.UserDataWriter
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.ProgressDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.german4k.German4kAngesagt
import tv.own.owntv.core.german4k.German4kAngesagtKarte
import tv.own.owntv.core.german4k.German4kAngesagtQuelle
import tv.own.owntv.core.german4k.German4kAngesagtReader
import tv.own.owntv.core.german4k.German4kAngesagtRegale
import tv.own.owntv.core.german4k.German4kAngesagtRepository
import tv.own.owntv.core.german4k.German4kDetails
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.repository.SeriesRepository
import tv.own.owntv.core.settings.SettingsRepository

/** German4K 3.2: Was „▶ Abspielen" im Hero startet. */
sealed interface AngesagtZiel {
    data class Film(val movieId: Long, val positionMs: Long) : AngesagtZiel
    data class Folge(val seriesId: Long, val episodeId: Long, val positionMs: Long) : AngesagtZiel
}

/**
 * German4K 3.2 (N1/N2): Angesagt & Top 10 für Startseite, Filme und Serien.
 *
 * Ein eigenes ViewModel statt Zusätze in Home-/Movie-/SeriesViewModel: die drei sind schon an der
 * Grenze von `viewModelOf` (22 Werte), und so bleibt der Eingriff in den OwnTV-Code klein. Die Regeln
 * (Abgleich, Fassungswahl, Erwachsene, Kinderprofil) stehen im Core ([German4kAngesagtReader]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class German4kAngesagtViewModel(
    private val reader: German4kAngesagtReader,
    private val repo: German4kAngesagtRepository,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val seriesDao: SeriesDao,
    private val sourceDao: SourceDao,
    private val xtream: XtreamClient,
    private val favoriteDao: FavoriteDao,
    private val userDataWriter: UserDataWriter,
    private val progressDao: ProgressDao,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    private val neuLadenTick = MutableStateFlow(0)

    /** Alle Regale für das aktive Profil; leer = Regale aus (keine URL, kein Netz, zu wenig Treffer). */
    val regale: StateFlow<German4kAngesagtRegale> =
        combine(settings.activeProfileId, repo.datei, neuLadenTick) { pid, _, _ -> pid }
            .mapLatest { pid -> runCatching { reader.regale(profil(pid) ?: -1L) }.getOrDefault(German4kAngesagtRegale()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), German4kAngesagtRegale())

    /** Die #1 der Startseite = der Hero. */
    private val hero = regale.map { it.start.firstOrNull() }.distinctUntilChanged()

    /** Handlung, Noten, Trailer und Querbild des Heros — ein `get_vod_info`/`get_series_info` wie die Detailseite. */
    val heroDetails: StateFlow<German4kDetails?> = hero
        .mapLatest { k -> k?.let { details(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val heroFavorit: StateFlow<Boolean> = combine(hero, settings.activeProfileId) { k, pid -> k to pid }
        .flatMapLatest { (k, pid) ->
            if (k == null || pid < 0) flowOf(false)
            else favoriteDao.isFavorite(pid, if (k.serie) MediaType.SERIES else MediaType.MOVIE, k.itemId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Datei holen (höchstens alle 6 h, der Riegel sitzt im Repository) — beim Start und jedes Mal,
        // wenn die Panel-Antwort eine (neue) Adresse bringt oder das Feature abschaltet.
        viewModelScope.launch {
            German4kAngesagtQuelle.flow.collect { repo.auffrischen() }
        }
    }

    /** Beim Öffnen eines Bildschirms: Katalog kann sich seit dem letzten Mal geändert haben (Abgleich neu). */
    fun neuLaden() {
        neuLadenTick.value = neuLadenTick.value + 1
        viewModelScope.launch { repo.auffrischen() }
    }

    /** Mess-Ereignis (M1): `angesagt_klick` bzw. `top10_klick`, detail = Rang + Quelle. */
    fun klick(karte: German4kAngesagtKarte, top10: Boolean, wo: String) {
        repo.ereignis(
            aktion = if (top10) AKTION_TOP10 else AKTION_ANGESAGT,
            detail = "rang=${karte.rang};platz=${karte.platz};quelle=$wo;art=${if (karte.serie) ART_SERIE else ART_FILM};tmdb=${karte.tmdb}",
        )
    }

    fun favoritUmschalten(karte: German4kAngesagtKarte) {
        viewModelScope.launch(Dispatchers.IO) {
            val pid = profil(settings.activeProfileId.first()) ?: return@launch
            val typ = if (karte.serie) MediaType.SERIES else MediaType.MOVIE
            if (favoriteDao.exists(pid, typ, karte.itemId)) userDataWriter.removeFavorite(pid, typ, karte.itemId)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = typ, itemId = karte.itemId))
        }
    }

    /**
     * „▶ Abspielen" im Hero: Film = Standard-Fassung (schon gewählt) an der gespeicherten Stelle;
     * Serie = Weiterschauen, sonst S1E1 ([German4kAngesagt.standardFolge]). Null = nichts spielbar.
     */
    suspend fun abspielZiel(karte: German4kAngesagtKarte): AngesagtZiel? = withContext(Dispatchers.IO) {
        val pid = profil(settings.activeProfileId.first()) ?: return@withContext null
        if (!karte.serie) {
            val p = progressDao.get(pid, MediaType.MOVIE, karte.itemId)
            val pos = p?.takeIf { it.durationMs <= 0 || it.positionMs < it.durationMs * FERTIG_ANTEIL }?.positionMs ?: 0L
            return@withContext AngesagtZiel.Film(karte.itemId, pos)
        }
        val serie = seriesDao.getSeriesById(karte.itemId) ?: return@withContext null
        runCatching { seriesRepository.loadEpisodes(serie) }
        val folgen = seriesDao.episodesBySeriesOnce(serie.id)
        val zuletzt = progressDao.lastWatchedEpisodeId(pid, serie.id)
        val zuletztP = zuletzt?.let { progressDao.get(pid, MediaType.EPISODE, it) }
        val fertig = zuletztP != null && zuletztP.durationMs > 0 && zuletztP.positionMs >= zuletztP.durationMs * FERTIG_ANTEIL
        val id = German4kAngesagt.standardFolge(
            folgen.map { German4kAngesagt.Folge(it.id, it.seasonNumber, it.episodeNumber) },
            zuletzt,
            fertig,
        ) ?: return@withContext null
        val pos = if (id == zuletzt && !fertig) zuletztP?.positionMs ?: 0L else 0L
        AngesagtZiel.Folge(serie.id, id, pos)
    }

    private suspend fun details(k: German4kAngesagtKarte): German4kDetails? = withContext(Dispatchers.IO) {
        runCatching {
            val source = sourceDao.getById(k.sourceId) ?: return@runCatching null
            if (source.type != SourceType.XTREAM || k.remoteId.isBlank()) return@runCatching null
            xtream.getDetails(source, serie = k.serie, id = k.remoteId)
        }.getOrNull()
    }

    private suspend fun profil(preferred: Long): Long? =
        if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null

    private companion object {
        const val AKTION_ANGESAGT = "angesagt_klick"
        const val AKTION_TOP10 = "top10_klick"
        const val ART_FILM = "film"
        const val ART_SERIE = "serie"
        /** Ab 95 % gilt ein Film/eine Folge als gesehen (wie die übrigen Fortschrittsbalken). */
        const val FERTIG_ANTEIL = 0.95
    }
}
