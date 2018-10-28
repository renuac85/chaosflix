package de.nicidienase.chaosflix.common.userdata.entities.progress

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.Index
import android.arch.persistence.room.PrimaryKey
import java.util.*

@Entity(tableName = "playback_progress",
		indices = arrayOf(Index(value = ["event_guid"],unique = true)))
data class PlaybackProgress (@PrimaryKey(autoGenerate = true)
                             val id: Long = 0,
                             @ColumnInfo(name = "event_guid")
                             var eventGuid: String,
                             var progress: Long,
                             @ColumnInfo(name = "watch_date")
                             val watchDate: Long){
	val date: Date
	get() = Date(watchDate)
}
