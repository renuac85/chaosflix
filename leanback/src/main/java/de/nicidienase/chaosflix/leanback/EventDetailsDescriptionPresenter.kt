package de.nicidienase.chaosflix.leanback

import android.content.Context
import android.support.v17.leanback.widget.Presenter
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.nicidienase.chaosflix.common.mediadata.entities.recording.persistence.PersistentEvent
import de.nicidienase.chaosflix.common.mediadata.entities.streaming.Room
import de.nicidienase.chaosflix.databinding.DetailViewBinding

class EventDetailsDescriptionPresenter(private val context: Context) : Presenter() {

	override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
		val binding = DetailViewBinding.inflate(LayoutInflater.from(context))
		return DescriptionViewHolder(binding.root, binding)
	}

	override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
		if (viewHolder !is DescriptionViewHolder) {context
			throw IllegalStateException("Wrong ViewHolder")
		}
		val dataHolder: DetailDataHolder
		if (item is PersistentEvent) {
			val persistentEvent = item
			val sb = StringBuilder()
			val speaker = TextUtils.join(", ", persistentEvent.persons)
			sb.append(persistentEvent.description)
					.append("\n")
					.append("\nreleased at: ").append(persistentEvent.releaseDate)
					.append("\nTags: ").append(android.text.TextUtils.join(", ", persistentEvent.tags!!))
			dataHolder = DetailDataHolder(persistentEvent.title,
					persistentEvent.subtitle,
					speaker,
					sb.toString())
		} else if (item is Room) {
			val (_, schedulename, _, _, display) = item
			dataHolder = DetailDataHolder(display, schedulename, "", "")
		} else {
			Log.e(TAG, "Item is neither PersistentEvent nor Room, this should not be happening")
			dataHolder = DetailDataHolder("", "", "", "")
		}
		viewHolder.binding.item = dataHolder
	}

	inner class DetailDataHolder internal constructor(val title: String, val subtitle: String?, val speakers: String, val description: String) {

		internal constructor(event: PersistentEvent) : this(
				event.title,
				event.subtitle,
				TextUtils.join(", ", event.persons!!),
				StringBuilder().append(event.description)
						.append("\n")
						.append("\nreleased at: ").append(event.releaseDate)
						.append("\nTags: ")
						.append(android.text.TextUtils.join(", ", event.tags!!))
						.toString())

		internal constructor(room: Room) : this(
				room.display,
				room.schedulename,
				"",
				"")
	}

	inner class DescriptionViewHolder(view: View, val binding: DetailViewBinding) : Presenter.ViewHolder(view)

	override fun onUnbindViewHolder(vh: Presenter.ViewHolder) {}

	companion object {
		private val TAG = EventDetailsDescriptionPresenter::class.java.simpleName
	}
}
