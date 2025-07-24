package com.hwork.simakohost.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class BackendApiManager private constructor(context: Context) { 
    // This singleton manages API calls to backend services with:
    // ✅ Cached Retrofit instances (no recreation on every SMS)
    // ✅ Dynamic URL updates (recreates only when URLs change)
    // ✅ Automatic fallback (tries both backends intelligently)
    // ✅ Minimal overhead (fast SMS processing)
    
    companion object {
        private const val TAG = "BackendApiManager"
        private const val PREFS_NAME = "simako_backend_prefs"
        private const val PREF_FLASK_URL = "flask_base_url"
        private const val PREF_NODEJS_URL = "nodejs_base_url"
        private const val PREF_ACTIVE_BACKEND = "active_backend"
        
        // Default URLs - Change these to your PC's IP address
        private const val DEFAULT_FLASK_URL = "http://192.168.1.32:5000/"    // ✅ Replace with YOUR PC's IP
        private const val DEFAULT_NODEJS_URL = "http://192.168.1.32:3000/"   // ✅ Replace with YOUR PC's IP
        @Volatile
        private var INSTANCE: BackendApiManager? = null
        
        fun getInstance(context: Context): BackendApiManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackendApiManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // Cached API instances with lazy initialization
    private var _flaskApi: SimakoBackendApi? = null
    private var _nodejsApi: SimakoBackendApi? = null
    private var _lastFlaskUrl: String? = null
    private var _lastNodejsUrl: String? = null

    // Flask API instance - Cached with URL change detection
    private fun createFlaskApi(): SimakoBackendApi {
        val currentUrl = getFlaskBaseUrl()
        
        // Return cached instance if URL hasn't changed
        if (_flaskApi != null && _lastFlaskUrl == currentUrl) {
            return _flaskApi!!
        }
        
        // Create new instance only if URL changed
        Log.d(TAG, "Creating new Flask API instance for URL: $currentUrl")
        val flaskRetrofit = Retrofit.Builder()
            .baseUrl(currentUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        _flaskApi = flaskRetrofit.create(SimakoBackendApi::class.java)
        _lastFlaskUrl = currentUrl
        return _flaskApi!!
    }

    val flaskApi: SimakoBackendApi get() = createFlaskApi()

    // Node.js API instance - Cached with URL change detection
    private fun createNodejsApi(): SimakoBackendApi {
        val currentUrl = getNodejsBaseUrl()
        
        // Return cached instance if URL hasn't changed
        if (_nodejsApi != null && _lastNodejsUrl == currentUrl) {
            return _nodejsApi!!
        }
        
        // Create new instance only if URL changed
        Log.d(TAG, "Creating new Node.js API instance for URL: $currentUrl")
        val nodejsRetrofit = Retrofit.Builder()
            .baseUrl(currentUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        
        _nodejsApi = nodejsRetrofit.create(SimakoBackendApi::class.java)
        _lastNodejsUrl = currentUrl
        return _nodejsApi!!
    }

    val nodejsApi: SimakoBackendApi get() = createNodejsApi()    // Get the active API based on user preference
    fun getActiveApi(): SimakoBackendApi {
        return when (getActiveBackend()) {
            "nodejs" -> nodejsApi
            "flask" -> flaskApi
            else -> flaskApi // Default to Flask
        }
    }
    
    // Get API with automatic fallback
    fun getAvailableApi(): SimakoBackendApi {
        val activeBackend = getActiveBackend()
        return when (activeBackend) {
            "nodejs" -> nodejsApi
            "flask" -> flaskApi
            else -> flaskApi
        }
    }
    
    // Try both backends in order of preference
    suspend fun <T> executeWithFallback(operation: suspend (SimakoBackendApi) -> T): T? {
        val primaryBackend = getActiveBackend()
        val backends = if (primaryBackend == "nodejs") {
            listOf("nodejs" to nodejsApi, "flask" to flaskApi)
        } else {
            listOf("flask" to flaskApi, "nodejs" to nodejsApi)
        }
        
        for ((backendName, api) in backends) {
            try {
                Log.d(TAG, "Trying $backendName backend...")
                val result = operation(api)
                Log.d(TAG, "✅ $backendName backend succeeded")
                return result
            } catch (e: Exception) {
                Log.w(TAG, "❌ $backendName backend failed: ${e.message}")
                continue
            }
        }
        
        Log.e(TAG, "🚨 All backends failed!")
        return null
    }
    
    // Settings management
    fun getFlaskBaseUrl(): String {
        return prefs.getString(PREF_FLASK_URL, DEFAULT_FLASK_URL) ?: DEFAULT_FLASK_URL
    }
    
    fun getNodejsBaseUrl(): String {
        return prefs.getString(PREF_NODEJS_URL, DEFAULT_NODEJS_URL) ?: DEFAULT_NODEJS_URL
    }
    
    fun getActiveBackend(): String {
        return prefs.getString(PREF_ACTIVE_BACKEND, "flask") ?: "flask"
    }
    
    fun setFlaskBaseUrl(url: String) {
        prefs.edit().putString(PREF_FLASK_URL, url).apply()
        // Force recreation of Flask API instance on next access
        _flaskApi = null
        _lastFlaskUrl = null
        Log.d(TAG, "Flask URL updated, will recreate API instance on next use")
    }
    
    fun setNodejsBaseUrl(url: String) {
        prefs.edit().putString(PREF_NODEJS_URL, url).apply()
        // Force recreation of Node.js API instance on next access
        _nodejsApi = null
        _lastNodejsUrl = null
        Log.d(TAG, "Node.js URL updated, will recreate API instance on next use")
    }
    
    fun setActiveBackend(backend: String) {
        if (backend in listOf("flask", "nodejs")) {
            prefs.edit().putString(PREF_ACTIVE_BACKEND, backend).apply()
            Log.d(TAG, "Active backend changed to: $backend")
        }
    }
    
    // Manual refresh method for when settings change
    fun refreshApiInstances() {
        Log.d(TAG, "Manually refreshing all API instances...")
        _flaskApi = null
        _nodejsApi = null
        _lastFlaskUrl = null
        _lastNodejsUrl = null
    }
    
    // Health check for both backends
    suspend fun checkBackendHealth(): Map<String, Boolean> {
        val results = mutableMapOf<String, Boolean>()
        
        try {
            val flaskResponse = flaskApi.getHealth()
            results["flask"] = flaskResponse.isSuccessful
            Log.d(TAG, "Flask health check: ${flaskResponse.isSuccessful}")
        } catch (e: Exception) {
            results["flask"] = false
            Log.e(TAG, "Flask health check failed: ${e.message}")
        }
        
        try {
            val nodejsResponse = nodejsApi.getHealth()
            results["nodejs"] = nodejsResponse.isSuccessful
            Log.d(TAG, "Node.js health check: ${nodejsResponse.isSuccessful}")
        } catch (e: Exception) {
            results["nodejs"] = false
            Log.e(TAG, "Node.js health check failed: ${e.message}")
        }
        
        return results
    }
}
