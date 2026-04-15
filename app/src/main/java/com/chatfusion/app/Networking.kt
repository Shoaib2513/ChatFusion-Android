package com.chatfusion.app

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


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
    var category: String? = null 
)


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


data class NewsArticle(
    val title: String,
    val link: String,
    val og: String? = null,
    val sourceName: String = "News",
    var category: String? = null
)


interface ApiService {
    
    @GET("news")
    suspend fun getInshortsNews(@Query("category") category: String): InshortsResponse

    
    @GET("https://api.rss2json.com/v1/api.json")
    suspend fun getRssNews(@Query("rss_url") rssUrl: String): RssResponse
}


object RetrofitClient {
    private const val BASE_URL = "https://inshorts.deta.dev/"

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
