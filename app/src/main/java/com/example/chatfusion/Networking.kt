package com.chatfusion.app

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Advanced Networking - Real-time Indian News (Inshorts)
data class InshortsResponse(
    val success: Boolean,
    val data: List<InshortsArticle>
)

data class InshortsArticle(
    val title: String,
    val content: String,
    @SerializedName("readMoreUrl") val link: String?,
    @SerializedName("imageUrl") val og: String?,
    val author: String,
    val date: String,
    val time: String,
    val sourceName: String?,
    var category: String? = null // Locally assigned
)

// RSS to JSON Response model
data class RssResponse(
    val status: String,
    val items: List<RssItem>
)

data class RssItem(
    val title: String,
    val link: String,
    val thumbnail: String?,
    val author: String,
    val pubDate: String,
    val content: String,
    val description: String
)

// Legacy models for compatibility if needed elsewhere
data class NewsArticle(
    val title: String,
    val link: String,
    val og: String? = null,
    val sourceName: String = "News",
    var category: String? = null
)

// Advanced Networking with Retrofit - API Interface
interface ApiService {
    // Fetching real-time news from Inshorts (very fresh for India)
    @GET("news")
    suspend fun getInshortsNews(@Query("category") category: String): InshortsResponse

    // Fallback: Fetching news via RSS-to-JSON (Times of India)
    @GET("https://api.rss2json.com/v1/api.json")
    suspend fun getRssNews(@Query("rss_url") rssUrl: String): RssResponse
}

// Advanced Networking with Retrofit - Retrofit Client
object RetrofitClient {
    private const val BASE_URL = "https://inshortsapi.vercel.app/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}
