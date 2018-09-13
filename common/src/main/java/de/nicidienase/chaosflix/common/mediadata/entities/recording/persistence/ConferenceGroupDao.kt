package de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence

import android.arch.lifecycle.LiveData
import android.arch.persistence.room.Dao
import android.arch.persistence.room.Query

@Dao
abstract class ConferenceGroupDao : BaseDao<ConferenceGroup>() {
	@Query("SELECT * FROM conference_group ORDER BY order_index")
	abstract fun getAll(): LiveData<List<ConferenceGroup>>

	@Query("SELECT * FROM conference_group WHERE name = :name LIMIT 1")
	abstract fun getConferenceGroupByName(name: String): ConferenceGroup?

	override fun updateOrInsertInternal(item: ConferenceGroup) {
		if (item.id != 0L) {
			update(item)
		} else {
			val existingGroup = getConferenceGroupByName(item.name)
			if (existingGroup != null) {
				item.id = existingGroup.id
				update(item)
			} else {
				item.id = insert(item)
			}
		}
	}
}