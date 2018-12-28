package de.nicidienase.chaosflix.common.mediadata.entities.recording

import com.google.gson.annotations.SerializedName

data class EventDto(@SerializedName("conference_id")
                    var conferenceId: Long = 0,
                    var guid: String = "",
                    var title: String = "",
                    var subtitle: String? = "",
                    var slug: String = "",
                    var link: String? = "",
                    var description: String? = "",
                    @SerializedName("original_language")
                    var originalLanguage: String = "",
                    var persons: Array<String>?,
                    var tags: Array<String>?,
                    var date: String? = "",
                    @SerializedName("release_date")
                    var releaseDate: String = "",
                    @SerializedName("updated_at")
                    var updatedAt: String = "",
                    var length: Long = 0,
                    @SerializedName("thumb_url")
                    var thumbUrl: String = "",
                    @SerializedName("poster_url")
                    var posterUrl: String = "",
                    @SerializedName("frontend_link")
                    var frontendLink: String? = "",
                    var url: String = "",
                    @SerializedName("conference_url")
                    var conferenceUrl: String = "",
                    var recordings: List<RecordingDto>?,
                    var related: List<RelatedEventDto>?,
                    @SerializedName("promoted")
                    var isPromoted: Boolean = false
) : Comparable<EventDto> {

	var eventID: Long
	@SerializedName("view_count")
	var viewCount: Int = 0

	init {
		val strings = url.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
		try {
			eventID = strings[strings.size - 1].toLong()
		} catch (ex: NumberFormatException) {
			eventID = 0
		}

		val split = conferenceUrl?.split("/".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
		if (split != null) {
			conferenceId = (split[split.size - 1]).toLong()
		}
	}

	fun getExtendedDescription(): String = "$description\n\nreleased at: $releaseDate\n\nTags: ${tags?.joinToString(", ")}"

	fun getSpeakerString(): String? = persons?.joinToString(", ")

	override fun compareTo(other: EventDto): Int {
		return slug.compareTo(other.slug)
	}

	override fun equals(other: Any?): Boolean {
		return if (other is EventDto) {
			guid == other.guid
		} else {
			super.equals(other)
		}

	}
}
