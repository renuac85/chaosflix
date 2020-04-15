package de.nicidienase.chaosflix.common.mediadata

import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import de.nicidienase.chaosflix.common.ChaosflixDatabase
import de.nicidienase.chaosflix.common.mediadata.entities.recording.ConferencesWrapper
import de.nicidienase.chaosflix.common.mediadata.entities.recording.EventDto
import de.nicidienase.chaosflix.common.mediadata.entities.recording.RecordingDto
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.Conference
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.ConferenceGroup
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.Event
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.EventDao
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.Recording
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.RecordingDao
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.RelatedEvent
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.RelatedEventDao
import de.nicidienase.chaosflix.common.mediadata.network.RecordingService
import de.nicidienase.chaosflix.common.userdata.entities.progress.PlaybackProgressDao
import de.nicidienase.chaosflix.common.userdata.entities.progress.ProgressEventView
import de.nicidienase.chaosflix.common.userdata.entities.recommendations.Recommendation
import de.nicidienase.chaosflix.common.userdata.entities.recommendations.RecommendationDao
import de.nicidienase.chaosflix.common.userdata.entities.recommendations.RecommendationEventView
import de.nicidienase.chaosflix.common.userdata.entities.watchlist.WatchlistItem
import de.nicidienase.chaosflix.common.userdata.entities.watchlist.WatchlistItemDao
import de.nicidienase.chaosflix.common.util.ConferenceUtil
import de.nicidienase.chaosflix.common.util.LiveEvent
import de.nicidienase.chaosflix.common.util.SingleLiveEvent
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response

class MediaRepository(
    private val recordingApi: RecordingService,
    private val database: ChaosflixDatabase
) {

    private val supervisorJob = SupervisorJob()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    private val conferenceGroupDao by lazy { database.conferenceGroupDao() }
    private val conferenceDao by lazy { database.conferenceDao() }
    private val eventDao: EventDao by lazy { database.eventDao() }
    private val recordingDao: RecordingDao by lazy { database.recordingDao() }
    private val relatedEventDao: RelatedEventDao by lazy { database.relatedEventDao() }
    private val watchlistItemDao: WatchlistItemDao by lazy { database.watchlistItemDao() }
    private val playbackProgressDao: PlaybackProgressDao by lazy { database.playbackProgressDao() }
    private val recommendationDao: RecommendationDao by lazy { database.recommendationDao() }

    suspend fun getEventSync(eventId: Long) = eventDao.findEventByIdSync(eventId)

    fun getEvent(eventId: Long): LiveData<Event?> = eventDao.findEventById(eventId)

    fun updateConferencesAndGroups(): SingleLiveEvent<LiveEvent<State, List<Conference>, String>> {
        val updateState = SingleLiveEvent<LiveEvent<State, List<Conference>, String>>()
            coroutineScope.launch(Dispatchers.IO) {
                updateState.postValue(LiveEvent(state = State.RUNNING))
                try {
                    val response = recordingApi.getConferencesWrapperSuspending()
                    if (response.isSuccessful) {
                        val conferencesWrapper = response.body()
                        if (conferencesWrapper != null) {
                            val saveConferences = saveConferences(conferencesWrapper)
                            updateState.postValue(LiveEvent(State.DONE, data = saveConferences))
                        } else {
                            updateState.postValue(LiveEvent(State.DONE, error = "Error updating conferences."))
                        }
                    } else {
                        Log.e(TAG, "Error: ${response.message()} ${response.errorBody()}")
                    }
                } catch (e: IOException) {
                    updateState.postValue(LiveEvent(State.DONE, error = e.message))
                } catch (e: Exception) {
                    updateState.postValue(LiveEvent(State.DONE, error = "Error updating Conferences (${e.cause})"))
                    e.printStackTrace()
                }
        }
        return updateState
    }

    fun updateEventsForConference(conference: Conference): LiveData<LiveEvent<State, List<Event>, String>> {
        val updateState = SingleLiveEvent<LiveEvent<State, List<Event>, String>>()
        updateState.postValue(LiveEvent(State.RUNNING))
        coroutineScope.launch {
            try {
                val list =
                    updateEventsForConferencesSuspending(conference)
                updateState.postValue(LiveEvent(State.DONE, data = list))
            } catch (e: IOException) {
                updateState.postValue(LiveEvent(State.DONE, error = e.message))
            } catch (e: Exception) {
                updateState.postValue(LiveEvent(State.DONE, error = "Error updating Events for ${conference.acronym} (${e.cause})"))
                e.printStackTrace()
            }
        }
        return updateState }

    private suspend fun updateEventsForConferencesSuspending(conference: Conference): List<Event> {
        val response = recordingApi.getConferenceByNameSuspending(conference.acronym)
        return if (response.isSuccessful) {
            val conferenceByName = response.body()
            val events = conferenceByName?.events
            if (events != null) {
                saveEvents(conference, events)
            } else {
                emptyList()
            }
        } else {
            Log.e(TAG, response.message())
            emptyList()
        }
    }

    suspend fun updateRecordingsForEvent(event: Event): List<Recording>? {
        return try {
            val response = recordingApi.getEventByGUIDSuspending(event.guid)
            return if (response.isSuccessful) {
                val eventDto = response.body()
                eventDto?.let { saveEvent(it) }
                val recordingDtos = eventDto?.recordings
                if (recordingDtos != null) {
                    saveRecordings(event, recordingDtos)
                } else {
                    null
                }
            } else {
                Log.e(TAG, "Error: ${response.message()} ${response.errorBody()}")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun updateSingleEvent(guid: String): Event? = withContext(Dispatchers.IO) {
        val response = recordingApi.getEventByGUIDSuspending(guid)
        if (!response.isSuccessful) {
            Log.e(TAG, "Error: ${response.message()} ${response.errorBody()}")
            return@withContext null
        }
        val event = response.body()
        return@withContext if (event != null) {
            try {
                saveEvent(event)
            } catch (ex: IllegalArgumentException) {
                Log.e(TAG, "could not save event", ex)
                null
            }
        } else {
            null
        }
    }

    @WorkerThread
    suspend fun deleteNonUserData() = withContext(Dispatchers.IO) {
        with(database) {
            conferenceGroupDao().delete()
            conferenceDao().delete()
            eventDao().delete()
            recordingDao().delete()
            relatedEventDao().delete()
        }
    }

    fun getBookmark(guid: String): LiveData<WatchlistItem?> = database.watchlistItemDao().getItemForEvent(guid)

    suspend fun addBookmark(guid: String) = withContext(Dispatchers.IO) {
        database.watchlistItemDao().saveItem(WatchlistItem(eventGuid = guid))
    }

    suspend fun deleteBookmark(guid: String) = withContext(Dispatchers.IO) {
        database.watchlistItemDao().deleteItem(guid)
    }

    @WorkerThread
    suspend fun saveConferences(conferencesWrapper: ConferencesWrapper): List<Conference> {
        return conferencesWrapper.conferencesMap.map { entry ->
            val conferenceGroup: ConferenceGroup = getOrCreateConferenceGroup(entry.key)
            val conferenceList = entry.value
                .map { Conference(it) }
                .map { it.conferenceGroupId = conferenceGroup.id; it }
            conferenceDao.updateOrInsert(*conferenceList.toTypedArray())
            conferenceGroupDao.deleteEmptyGroups()
            return@map conferenceList
        }.flatten()
    }

    private suspend fun getOrCreateConferenceGroup(name: String): ConferenceGroup {
        val conferenceGroup: ConferenceGroup? =
            conferenceGroupDao.getConferenceGroupByName(name)
        if (conferenceGroup != null) {
            return conferenceGroup
        }
        val group = ConferenceGroup(name)
        val index = ConferenceUtil.orderedConferencesList.indexOf(group.name)
        if (index != -1)
            group.index = index
        else if (group.name == "other conferences")
            group.index = 1_000_001
        group.id = conferenceGroupDao.insert(group)
        return group
    }

    private suspend fun saveEvents(persistentConference: Conference, events: List<EventDto>): List<Event> {
        val persistantEvents = events.map { Event(it, persistentConference.id) }
        eventDao.updateOrInsert(*persistantEvents.toTypedArray())
        persistantEvents.forEach {
            it.related = saveRelatedEvents(it)
        }
        return persistantEvents
    }

    private suspend fun saveEvent(event: EventDto): Event = withContext(Dispatchers.IO) {
        val acronym = event.conferenceUrl.split("/").last()
        val conferenceId = conferenceDao.findConferenceByAcronym(acronym)?.id
                ?: updateConferencesAndGet(acronym)?.id

        checkNotNull(conferenceId) { "Could not find Conference for event" }

        val persistentEvent = Event(event, conferenceId)
        val id = eventDao.updateOrInsert(persistentEvent)
        persistentEvent.id = id
        return@withContext persistentEvent
    }

    private suspend fun updateConferencesAndGet(acronym: String): Conference? {
        val response: Response<ConferencesWrapper>? = recordingApi.getConferencesWrapper()
        val conferences = response?.body()?.let { conferencesWrapper ->
            return@let saveConferences(conferencesWrapper)
        }
        return conferences?.find { it.acronym == acronym }
    }

    private suspend fun saveRelatedEvents(event: Event): List<RelatedEvent> {
        val list: List<RelatedEvent> = event.related?.map { it.parentEventId = event.id; it } ?: emptyList()
        relatedEventDao.updateOrInsert(*list.toTypedArray())
        return list
    }

    private suspend fun saveRecordings(event: Event, recordings: List<RecordingDto>): List<Recording> {
        val persistentRecordings = recordings.map { Recording(it, event.id) }
        recordingDao.updateOrInsert(*persistentRecordings.toTypedArray())
        return persistentRecordings
    }

    suspend fun findEventForUri(data: Uri): Event? {
        var event: Event? = eventDao.findEventForFrontendUrl(data.toString())

        val pathSegment = data.lastPathSegment
        if (event == null && pathSegment != null) {
            event = searchEvent(pathSegment)
        }

        return event
    }

    suspend fun findEvents(queryString: String, page: Int = 1): SearchResponse? = withContext(Dispatchers.IO) {
        val eventsResponse = recordingApi.searchEvents(queryString, page)
        return@withContext if (eventsResponse.isSuccessful) {
            val total = eventsResponse.headers()["total"]?.toInt() ?: 0
            val links = parseLink(eventsResponse.headers()["link"])
            val events = eventsResponse.body()?.events?.map { saveEvent(it) } ?: emptyList()

            SearchResponse(events, total, links)
        } else {
            null
        }
    }

    suspend fun findConferenceForUri(data: Uri): Conference? {
        val acronym = data.lastPathSegment
        if (acronym != null) {
            return conferenceDao.findConferenceByAcronymSuspend(acronym)
        } else {
            error("missing path")
        }
    }

    suspend fun findEventByTitle(title: String): Event? {
        return eventDao.findEventByTitleSuspend(title) ?: searchEvent(title, true)
    }

    private suspend fun searchEvent(queryString: String, updateConference: Boolean = false): Event? {
        val response = recordingApi.searchEvents(queryString)
        if (!response.isSuccessful) {
            Log.e(TAG, "Error: ${response.message()} ${response.errorBody()}")
            return null
        }
        val searchEvents = response.body()
        if (searchEvents != null && searchEvents.events.isNotEmpty()) {
            val eventDto = searchEvents.events[0]
            try {
                val conference = updateConferencesAndGet(eventDto.conferenceUrl.split("/").last())
                if (updateConference && conference != null) {
                    updateEventsForConference(conference)
                }
                if (conference?.id != null) {
                    val event = Event(eventDto, conference.id)
                    eventDao.updateOrInsert(event)
                    return event
                }
            } catch (ex: IllegalArgumentException) {
                Log.e(TAG, "could not load conference", ex)
                return null
            }
        }
        return null
    }

    suspend fun getAllOfflineEvents(): List<Long> = database.offlineEventDao().getAllDownloadReferences()

    suspend fun saveOrUpdate(watchlistItem: WatchlistItem) {
        watchlistItemDao.updateOrInsert(watchlistItem)
    }

    fun getReleatedEvents(eventId: Long): LiveData<List<Event>> {
        coroutineScope.launch {
            val relatedEvents = relatedEventDao.getRelatedEventsForEventSuspend(eventId)
            relatedEvents.forEach {
                updateSingleEvent(it.relatedEventGuid)
            }
        }
        return relatedEventDao.newGetReletedEventsForEvent(eventId)
    }

    suspend fun findEventForGuid(guid: String): Event? {
        return eventDao.findEventByGuidSync(guid) ?: updateSingleEvent(guid)
    }
    fun findRecordingsForEvent(eventId: Long): LiveData<List<Recording>> {
        return recordingDao.findRecordingByEvent(eventId)
    }

    suspend fun getEventsInProgress(): List<ProgressEventView> {
        val progress = playbackProgressDao.getAllWithEventSync()
        progress.forEach { it.event?.progress = it.progress.progress }
        return progress
    }

    suspend fun getTopEvents(count: Int): List<Event> {
        return eventDao.getTopViewedEvents(count)
    }

    suspend fun getNewestConferences(count: Int): List<Conference> {
        return conferenceDao.getLatestConferences(count)
    }

    suspend fun getBookmarkedEvents(): List<Event> = eventDao.findBookmarkedEventsSync()

    suspend fun getHomescreenRecommendations(): List<Event> {
        return getTopEvents(10)
    }

    suspend fun getActiveRecommendation(channel: String): List<RecommendationEventView> {
        return recommendationDao.getAllForChannel(channel)
    }

    suspend fun setRecommendationIdForEvent(event: Event, id: Long, channel: String) = withContext(Dispatchers.IO) {
        recommendationDao.insert(Recommendation(eventGuid = event.guid, channel = channel, programmId = id))
    }

    suspend fun resetRecommendationId(programmId: Long) = withContext(Dispatchers.IO) {
        recommendationDao.markDismissed(programmId)
    }

    data class SearchResponse(val events: List<Event>, val total: Int, val links: Map<String, String>) {
        val hasNext: Boolean = hasLink("next")
        val hasPrev: Boolean = hasLink("prev")

        private fun hasLink(key: String) = links.keys.contains(key)
    }

    companion object {
        private val TAG = MediaRepository::class.java.simpleName

        fun parseLink(link: String?): Map<String, String> {
            if (link == null) {
                return emptyMap()
            }
            val links = link.split(",")
            return links.associate {
                val pair = it.split(";")
                pair[1].substringAfter("\"").substringBefore("\"") to pair[0].substringAfter("<").substringBefore(">")
            }
        }
    }

    enum class State {
        DONE, RUNNING
    }
}
