package de.nicidienase.chaosflix.common.mediadata.network

import android.content.res.Resources
import android.os.Build
import com.google.gson.Gson
import de.nicidienase.chaosflix.BuildConfig
import de.nicidienase.chaosflix.R
import de.nicidienase.chaosflix.common.SingletonHolder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiFactory private constructor(val res: Resources) {

    private val chaosflixUserAgent: String by lazy { buildUserAgent() }
    private val gsonConverterFactory: GsonConverterFactory by lazy { GsonConverterFactory.create(Gson()) }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(useragentInterceptor)
            .build()
    }

    val recordingApi: RecordingService by lazy {
        Retrofit.Builder()
            .baseUrl(res.getString(R.string.recording_url))
            .client(client)
            .addConverterFactory(gsonConverterFactory)
            .build()
            .create(RecordingService::class.java)
    }

    val streamingApi: StreamingApi by lazy { Retrofit.Builder()
        .baseUrl(BuildConfig.STREAMING_API_BASE_URL)
        .client(client)
        .addConverterFactory(gsonConverterFactory)
        .build()
        .create(StreamingApi::class.java) }

    val fahrplanMappingApi: FahrplanMappingService by lazy {
        Retrofit.Builder()
        .baseUrl("https://gist.githubusercontent.com")
        .client(client)
        .addConverterFactory(gsonConverterFactory)
        .build()
        .create(FahrplanMappingService::class.java)
    }

    private val useragentInterceptor: Interceptor = Interceptor { chain ->
        val requestWithUseragent = chain.request().newBuilder()
            .header("User-Agent", chaosflixUserAgent)
            .build()
        return@Interceptor chain.proceed(requestWithUseragent)
    }

    companion object : SingletonHolder<ApiFactory, Resources>(::ApiFactory) {

        private const val DEFAULT_TIMEOUT = 30L

        fun buildUserAgent(): String {
            val versionName = BuildConfig.VERSION_NAME
            val device = "${Build.BRAND} ${Build.MODEL}"
            val osVersion = "Android/${Build.VERSION.RELEASE}"
            return "chaosflix/$versionName $osVersion ($device)"
        }
    }
}