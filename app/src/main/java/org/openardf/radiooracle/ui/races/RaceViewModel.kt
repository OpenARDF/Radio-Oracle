package org.openardf.radiooracle.ui.races

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.openardf.radiooracle.backend.files.DesktopFileTransferUpload
import org.openardf.radiooracle.backend.files.AndroidEventSeriesImport
import org.openardf.radiooracle.backend.DataProcessor
import org.openardf.radiooracle.backend.room.entity.Race
import org.openardf.radiooracle.backend.room.entity.embeddeds.RaceData
import org.openardf.radiooracle.backend.room.enums.ProviderType
import org.openardf.radiooracle.ui.series.EventSeriesUiVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** ViewModel backing race selection, import/export, and provider download flows. */
class RaceViewModel : ViewModel() {
    private val dataProcessor = DataProcessor.get()
    private val _races: MutableStateFlow<List<Race>> = MutableStateFlow(emptyList())
    val races: StateFlow<List<Race>> get() = _races.asStateFlow()
    private val _raceListItems: MutableStateFlow<List<RaceListItem>> = MutableStateFlow(emptyList())
    val raceListItems: StateFlow<List<RaceListItem>> get() = _raceListItems.asStateFlow()
    private val _showStoredSeriesActions: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showStoredSeriesActions: StateFlow<Boolean> get() = _showStoredSeriesActions.asStateFlow()


    /** Creates a race on a background dispatcher. */
    fun createRace(
        race: Race
    ) = CoroutineScope(Dispatchers.IO).launch { dataProcessor.createRace(race) }

    /** Updates a race on a background dispatcher. */
    fun updateRace(
        race: Race
    ) = CoroutineScope(Dispatchers.IO).launch { dataProcessor.updateRace(race) }

    /** Deletes a race by id on a background dispatcher. */
    fun deleteRace(id: UUID) {
        CoroutineScope(Dispatchers.IO).launch {
            dataProcessor.deleteRace(id)
        }
    }

    /** Returns the local series name for this race when it belongs to a multi-event series. */
    fun seriesNameForRace(raceId: UUID): String? = runBlocking {
        dataProcessor.getEventSeriesForRace(raceId)
            ?.series
            ?.name
    }

    /** Saves an imported full-race payload on a background dispatcher. */
    fun saveRaceData(raceData: RaceData) = CoroutineScope(Dispatchers.IO).launch {
        dataProcessor.saveRaceData(raceData)
    }

    /** Imports a full race backup from a selected URI. */
    fun importRaceData(
        uri: Uri
    ) = runBlocking {
        dataProcessor.importRaceData(uri)
    }

    /** Imports a full race backup from downloaded Event File JSON. */
    fun importRaceData(
        jsonString: String
    ) = runBlocking {
        dataProcessor.importRaceData(jsonString)
    }

    /** Imports and saves a full Event Series package from a selected URI. */
    suspend fun importAndSaveEventSeriesPackage(uri: Uri): AndroidEventSeriesImport? {
        val eventSeriesImport = dataProcessor.importEventSeriesPackage(uri) ?: return null
        dataProcessor.saveEventSeriesImport(eventSeriesImport)
        return eventSeriesImport
    }

    /** Imports and saves a full Event Series package downloaded from desktop transfer bytes. */
    suspend fun importAndSaveEventSeriesPackage(bytes: ByteArray): AndroidEventSeriesImport? {
        val eventSeriesImport = dataProcessor.importEventSeriesPackage(bytes) ?: return null
        dataProcessor.saveEventSeriesImport(eventSeriesImport)
        return eventSeriesImport
    }

    /** Downloads race data from an online provider. */
    fun fetchProviderRaceData(providerType: ProviderType, apiKey: String, context: Context) =
        runBlocking {
            dataProcessor.fetchProviderRaceData(providerType, apiKey, context)
        }

    /** Exports a full race backup to a selected URI. */
    fun exportRaceData(
        uri: Uri, raceId: UUID
    ) = runBlocking {
        dataProcessor.exportRaceData(uri, raceId)
    }

    /** Exports a single Event File or the full Event Series package when the event is a series member. */
    fun exportRaceOrSeriesData(
        uri: Uri, raceId: UUID
    ) = runBlocking {
        dataProcessor.exportRaceOrSeriesData(uri, raceId)
    }

    /** Exports a full race backup as bytes for direct desktop upload. */
    suspend fun exportRaceDataBytes(raceId: UUID): ByteArray =
        dataProcessor.exportRaceDataBytes(raceId)

    /** Exports a single Event File or the full Event Series package as bytes. */
    suspend fun exportRaceOrSeriesDataBytes(raceId: UUID): ByteArray =
        dataProcessor.exportRaceOrSeriesDataBytes(raceId)

    /** Prepares the direct desktop upload for a single Event File or the containing Event Series package. */
    suspend fun desktopUploadForRaceOrSeries(raceId: UUID): DesktopFileTransferUpload =
        dataProcessor.desktopUploadForRaceOrSeries(raceId)

    /** Observes races and publishes them sorted by start time. */
    init {
        viewModelScope.launch {
            dataProcessor.getRaces().collect { races ->
                _races.value = races.sortedBy { it.startDateTime }
            }
        }
        viewModelScope.launch {
            dataProcessor.getEventSeries().collect { eventSeries ->
                _showStoredSeriesActions.value =
                    EventSeriesUiVisibility.showStoredSeriesActions(eventSeries.size)
            }
        }
        viewModelScope.launch {
            dataProcessor.getRaces()
                .combine(dataProcessor.getEventSeries()) { races, eventSeries ->
                    RaceListItems.build(races, eventSeries)
                }
                .collect { raceListItems ->
                    _raceListItems.value = raceListItems
                }
        }
    }
}
