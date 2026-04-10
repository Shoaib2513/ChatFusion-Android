package com.chatfusion.app

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

// Unit III: Advanced Networking with Retrofit - Data Model
data class NewsItem(
    val id: Int,
    val title: String,
    val body: String
)

// Unit III: Advanced Networking with Retrofit - API Interface
interface ApiService {
    @GET("posts")
    suspend fun getTrendingNews(): List<NewsItem>
}

// Unit III: Advanced Networking with Retrofit - Retrofit Client with Auth/Logging
object RetrofitClient {
    private const val BASE_URL = "https://jsonplaceholder.typicode.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            // Unit III: Authentication - Adding a dummy header/token for demonstration
            val requestBuilder = original.newBuilder()
                .header("Authorization", "Bearer dummy_token_for_syllabus")
                .method(original.method, original.body)
            val request = requestBuilder.build()
            chain.proceed(request)
        }
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
