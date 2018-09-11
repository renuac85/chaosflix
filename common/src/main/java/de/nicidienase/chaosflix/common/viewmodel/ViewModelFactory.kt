package de.nicidienase.chaosflix.common.viewmodel

import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProvider
import android.content.Context
import android.preference.PreferenceManager
import de.nicidienase.chaosflix.common.DatabaseFactory
import de.nicidienase.chaosflix.common.OfflineItemManager
import de.nicidienase.chaosflix.common.PreferencesManager
import de.nicidienase.chaosflix.common.mediadata.network.ApiFactory

class ViewModelFactory(context: Context) : ViewModelProvider.Factory {

	val apiFactory = ApiFactory(context.resources)

	val database = DatabaseFactory(context).mediaDatabase
	val recordingApi = apiFactory.recordingApi
	val streamingApi = apiFactory.streamingApi
	val preferencesManager =
			PreferencesManager(PreferenceManager.getDefaultSharedPreferences(context.applicationContext))
	val offlineItemManager =
			OfflineItemManager(context.applicationContext, database.offlineEventDao(),preferencesManager)

	@Suppress("UNCHECKED_CAST")
	override fun <T : ViewModel?> create(modelClass: Class<T>): T {
		if (modelClass.isAssignableFrom(BrowseViewModel::class.java)) {
			return BrowseViewModel(offlineItemManager, database, recordingApi, streamingApi, preferencesManager) as T

		} else if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
			return PlayerViewModel(database) as T

		} else if (modelClass.isAssignableFrom(DetailsViewModel::class.java)) {
			return DetailsViewModel(database, recordingApi, offlineItemManager, preferencesManager) as T

		} else {
			throw UnsupportedOperationException("The requested ViewModel is currently unsupported. " +
					"Please make sure to implement are correct creation of it. " +
					" Request: ${modelClass.getCanonicalName()}");
		}

	}

}