package com.chatfusion.app.ui.discover

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chatfusion.app.BuildConfig
import com.chatfusion.app.ChatActivity
import com.chatfusion.app.InshortsArticle
import com.chatfusion.app.RetrofitClient
import com.chatfusion.app.RssResponse
import com.chatfusion.app.User
import com.chatfusion.app.UserAdapter
import com.chatfusion.app.WebViewActivity
import com.chatfusion.app.databinding.FragmentDiscoverBinding
import com.chatfusion.app.databinding.ItemNewsBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
import org.json.JSONObject

class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!
    private lateinit var firestore: FirebaseFirestore
    private lateinit var suggestionAdapter: UserAdapter
    private lateinit var searchAdapter: UserAdapter
    
    private var suggestionListener: ListenerRegistration? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        firestore = FirebaseFirestore.getInstance()

        setupRecyclerViews()
        loadSuggestions()
        setupSearch()
        loadIndianNews()
    }

    private fun setupRecyclerViews() {
        suggestionAdapter = UserAdapter(showChatDetails = false) { user -> openChat(user) }
        binding.rvSuggestions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = suggestionAdapter
            setHasFixedSize(true)
        }

        searchAdapter = UserAdapter(showChatDetails = false) { user ->
            binding.searchView.hide()
            openChat(user)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }
    }

    // Advanced Networking - AI Powered News fallback when Inshorts is down
    private fun loadIndianNews() {
        binding.newsProgressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val allArticles = mutableListOf<InshortsArticle>()

                supervisorScope {
                    val techDeferred = async { RetrofitClient.instance.getInshortsNews("technology") }
                    val scienceDeferred = async { RetrofitClient.instance.getInshortsNews("science") }
                    val worldDeferred = async { RetrofitClient.instance.getInshortsNews("world") }

                    val techResponse = try { techDeferred.await() } catch (e: Exception) {
                        Log.w("DiscoverFragment", "Tech news unavailable: ${e.message}")
                        null
                    }
                    val scienceResponse = try { scienceDeferred.await() } catch (e: Exception) {
                        Log.w("DiscoverFragment", "Science news unavailable: ${e.message}")
                        null
                    }
                    val worldResponse = try { worldDeferred.await() } catch (e: Exception) {
                        Log.w("DiscoverFragment", "World news unavailable: ${e.message}")
                        null
                    }

                    techResponse?.data?.let { articles ->
                        articles.forEach { it.category = "Technology" }
                        allArticles.addAll(articles)
                    }
                    scienceResponse?.data?.let { articles ->
                        articles.forEach { it.category = "Science" }
                        allArticles.addAll(articles)
                    }
                    worldResponse?.data?.let { articles ->
                        articles.forEach { it.category = "World" }
                        allArticles.addAll(articles)
                    }
                }

                if (allArticles.isEmpty()) {
                    // Fallback to another Retrofit source (RSS)
                    fetchRssNews()
                } else {
                    binding.newsProgressBar.visibility = View.GONE
                    displayNews(allArticles)
                }

            } catch (e: Exception) {
                fetchRssNews()
            }
        }
    }

    private fun fetchRssNews() {
        lifecycleScope.launch {
            try {
                // Using RSS-to-JSON for Google News India
                val rssResponse = RetrofitClient.instance.getRssNews("https://news.google.com/rss?hl=en-IN&gl=IN&ceid=IN:en")
                
                if (rssResponse.status == "ok" && rssResponse.items.isNotEmpty()) {
                    binding.newsProgressBar.visibility = View.GONE
                    val articles = rssResponse.items.take(10).map { item ->
                        InshortsArticle(
                            title = item.title,
                            content = item.content.ifEmpty { item.description }.take(200),
                            link = item.link,
                            og = item.thumbnail,
                            author = item.author.ifEmpty { "Google News" },
                            date = item.pubDate,
                            time = "",
                            sourceName = "Google News",
                            category = "Trending"
                        )
                    }
                    displayNews(articles)
                } else {
                    fetchAINews()
                }
            } catch (e: Exception) {
                Log.e("DiscoverFragment", "RSS News fallback failed", e)
                fetchAINews()
            }
        }
    }

    private fun fetchAINews() {
        lifecycleScope.launch {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY
                )

                val prompt = """
                    Generate 5 trending tech and science news headlines for India today. 
                    Format the output as a JSON array of objects with keys: "title", "content", "sourceName", "category", "time".
                    Keep "content" short (one sentence). 
                    Example: [{"title": "ISRO launches new satellite", "content": "The mission was successful.", "sourceName": "AI Trends", "category": "Science", "time": "Just now"}]
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val responseText = response.text?.trim() ?: ""
                
                // Extract JSON from potential Markdown formatting
                val jsonStartIndex = responseText.indexOf("[")
                val jsonEndIndex = responseText.lastIndexOf("]") + 1
                
                if (jsonStartIndex != -1 && jsonEndIndex != -1) {
                    val jsonStr = responseText.substring(jsonStartIndex, jsonEndIndex)
                    val jsonArray = JSONArray(jsonStr)
                    val aiArticles = mutableListOf<InshortsArticle>()
                    
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        aiArticles.add(InshortsArticle(
                            title = obj.getString("title"),
                            content = obj.getString("content"),
                            link = "https://news.google.com",
                            og = null,
                            author = "ChatFusion AI",
                            date = "Today",
                            time = obj.getString("time"),
                            sourceName = obj.getString("sourceName"),
                            category = obj.getString("category")
                        ))
                    }
                    displayNews(aiArticles)
                } else {
                    hideNews()
                }
                binding.newsProgressBar.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("DiscoverFragment", "AI News generation failed", e)
                hideNews()
                binding.newsProgressBar.visibility = View.GONE
            }
        }
    }

    private fun displayNews(articles: List<InshortsArticle>) {
        val aiKeywords = listOf("AI", "Artificial Intelligence", "ChatGPT", "Gemini", "OpenAI", "Nvidia", "ISRO", "Startup", "India", "Bharat")
        
        val filteredArticles = articles.sortedByDescending { article ->
            val title = article.title.lowercase()
            aiKeywords.count { title.contains(it.lowercase()) }
        }.distinctBy { it.title }

        if (filteredArticles.isEmpty()) {
            hideNews()
            return
        }

        binding.layoutTrendingNews.visibility = View.VISIBLE
        binding.discoverDivider.visibility = View.VISIBLE
        binding.rvTrendingNews.adapter = NewsAdapter(filteredArticles.take(15)) { article ->
            val articleUrl = article.link ?: "https://www.inshorts.com"
            val intent = Intent(requireContext(), WebViewActivity::class.java)
            intent.putExtra("URL", articleUrl)
            intent.putExtra("TITLE", article.title)
            startActivity(intent)
        }
        binding.rvTrendingNews.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
    }

    private fun hideNews() {
        binding.layoutTrendingNews.visibility = View.GONE
        binding.discoverDivider.visibility = View.GONE
    }

    private class NewsAdapter(
        private val articles: List<InshortsArticle>,
        private val onItemClick: (InshortsArticle) -> Unit
    ) : RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

        class NewsViewHolder(val binding: ItemNewsBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
            val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return NewsViewHolder(binding)
        }

        override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
            val article = articles[position]
            holder.binding.tvNewsTitle.text = article.title
            holder.binding.tvNewsSource.text = "${article.sourceName ?: "Inshorts"} • ${article.category ?: "News"} • ${article.time}"
            
            holder.itemView.setOnClickListener { onItemClick(article) }
        }

        override fun getItemCount(): Int = articles.size
    }

    private fun openChat(user: User) {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        intent.putExtra("receiverId", user.uid)
        intent.putExtra("receiverName", user.name)
        startActivity(intent)
    }

    private fun loadSuggestions() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        binding.suggestionProgressBar.visibility = View.VISIBLE
        
        suggestionListener = firestore.collection("users")
            .limit(20)
            .addSnapshotListener { snapshot, e ->
                if (!isAdded) return@addSnapshotListener
                binding.suggestionProgressBar.visibility = View.GONE
                
                if (e != null) {
                    Log.e("DiscoverFragment", "Error loading suggestions", e)
                    Toast.makeText(context, "Connection error", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                
                val users = snapshot?.toObjects(User::class.java) ?: emptyList()
                val filteredList = users.filter { it.uid != currentUserId }
                
                suggestionAdapter.submitList(filteredList)
            }
    }

    private fun setupSearch() {
        binding.searchView.editText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable { searchUsers(query) }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun searchUsers(query: String) {
        if (!isAdded) return
        
        if (query.isEmpty()) {
            searchAdapter.submitList(emptyList())
            binding.tvNoResults.visibility = View.GONE
            binding.searchProgressBar.visibility = View.GONE
            return
        }

        binding.searchProgressBar.visibility = View.VISIBLE
        binding.tvNoResults.visibility = View.GONE

        val lowerQuery = query.lowercase()

        firestore.collection("users")
            .whereGreaterThanOrEqualTo("nameLower", lowerQuery)
            .whereLessThanOrEqualTo("nameLower", lowerQuery + "\uf8ff")
            .limit(15)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded) return@addOnSuccessListener
                binding.searchProgressBar.visibility = View.GONE
                
                val users = snapshot.toObjects(User::class.java)
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val filteredResults = users.filter { it.uid != currentUserId }
                
                searchAdapter.submitList(filteredResults)
                binding.tvNoResults.visibility = if (filteredResults.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e("DiscoverFragment", "Error searching users", e)
                binding.searchProgressBar.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        suggestionListener?.remove()
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        _binding = null
    }
}
