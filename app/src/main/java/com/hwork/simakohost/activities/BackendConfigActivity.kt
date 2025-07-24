package com.hwork.simakohost.activities

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.hwork.simakohost.R
import com.hwork.simakohost.network.BackendApiManager
import com.hwork.simakohost.service.SimakoBackendService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackendConfigActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "BackendConfigActivity"
    }
    
    private lateinit var flaskUrlEdit: EditText
    private lateinit var nodejsUrlEdit: EditText
    private lateinit var activeBackendSpinner: Spinner
    private lateinit var testFlaskButton: Button
    private lateinit var testNodejsButton: Button
    private lateinit var saveButton: Button
    private lateinit var statusText: TextView
    private lateinit var flaskStatusIcon: ImageView
    private lateinit var nodejsStatusIcon: ImageView
    
    private lateinit var apiManager: BackendApiManager
    private lateinit var backendService: SimakoBackendService
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backend_config)
        
        apiManager = BackendApiManager.getInstance(this)
        backendService = SimakoBackendService.getInstance(this)
        
        initializeViews()
        loadCurrentSettings()
        setupListeners()
        
        // Initial connectivity check
        checkBackendConnectivity()
    }
    
    private fun initializeViews() {
        flaskUrlEdit = findViewById(R.id.flask_url_edit)
        nodejsUrlEdit = findViewById(R.id.nodejs_url_edit)
        activeBackendSpinner = findViewById(R.id.active_backend_spinner)
        testFlaskButton = findViewById(R.id.test_flask_button)
        testNodejsButton = findViewById(R.id.test_nodejs_button)
        saveButton = findViewById(R.id.save_button)
        statusText = findViewById(R.id.status_text)
        flaskStatusIcon = findViewById(R.id.flask_status_icon)
        nodejsStatusIcon = findViewById(R.id.nodejs_status_icon)
        
        // Setup spinner
        val backendOptions = arrayOf("Flask", "Node.js")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, backendOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        activeBackendSpinner.adapter = adapter
    }
    
    private fun loadCurrentSettings() {
        flaskUrlEdit.setText(apiManager.getFlaskBaseUrl())
        nodejsUrlEdit.setText(apiManager.getNodejsBaseUrl())
        
        val activeBackend = apiManager.getActiveBackend()
        activeBackendSpinner.setSelection(if (activeBackend == "nodejs") 1 else 0)
    }
    
    private fun setupListeners() {
        testFlaskButton.setOnClickListener {
            testFlaskConnection()
        }
        
        testNodejsButton.setOnClickListener {
            testNodejsConnection()
        }
        
        saveButton.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun testFlaskConnection() {
        setButtonsEnabled(false)
        statusText.text = "Testing Flask connection..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiManager.flaskApi.getHealth()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        statusText.text = "Flask connection successful!"
                        flaskStatusIcon.setImageResource(android.R.drawable.presence_online)
                        Log.d(TAG, "Flask health check successful: ${response.body()}")
                    } else {
                        statusText.text = "Flask connection failed: ${response.code()}"
                        flaskStatusIcon.setImageResource(android.R.drawable.presence_offline)
                    }
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Flask connection error: ${e.message}"
                    flaskStatusIcon.setImageResource(android.R.drawable.presence_offline)
                    Log.e(TAG, "Flask health check failed", e)
                    setButtonsEnabled(true)
                }
            }
        }
    }
    
    private fun testNodejsConnection() {
        setButtonsEnabled(false)
        statusText.text = "Testing Node.js connection..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiManager.nodejsApi.getHealth()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        statusText.text = "Node.js connection successful!"
                        nodejsStatusIcon.setImageResource(android.R.drawable.presence_online)
                        Log.d(TAG, "Node.js health check successful: ${response.body()}")
                    } else {
                        statusText.text = "Node.js connection failed: ${response.code()}"
                        nodejsStatusIcon.setImageResource(android.R.drawable.presence_offline)
                    }
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Node.js connection error: ${e.message}"
                    nodejsStatusIcon.setImageResource(android.R.drawable.presence_offline)
                    Log.e(TAG, "Node.js health check failed", e)
                    setButtonsEnabled(true)
                }
            }
        }
    }
    
    private fun checkBackendConnectivity() {
        setButtonsEnabled(false)
        statusText.text = "Checking backend connectivity..."
        
        backendService.checkBackendConnectivity { healthStatus ->
            runOnUiThread {
                val flaskStatus = healthStatus["flask"] ?: false
                val nodejsStatus = healthStatus["nodejs"] ?: false
                
                flaskStatusIcon.setImageResource(
                    if (flaskStatus) android.R.drawable.presence_online 
                    else android.R.drawable.presence_offline
                )
                
                nodejsStatusIcon.setImageResource(
                    if (nodejsStatus) android.R.drawable.presence_online 
                    else android.R.drawable.presence_offline
                )
                
                statusText.text = when {
                    flaskStatus && nodejsStatus -> "Both backends are online"
                    flaskStatus -> "Flask backend online, Node.js offline"
                    nodejsStatus -> "Node.js backend online, Flask offline"
                    else -> "Both backends are offline"
                }
                
                setButtonsEnabled(true)
            }
        }
    }
    
    private fun saveSettings() {
        val flaskUrl = flaskUrlEdit.text.toString().trim()
        val nodejsUrl = nodejsUrlEdit.text.toString().trim()
        val activeBackend = if (activeBackendSpinner.selectedItemPosition == 1) "nodejs" else "flask"
        
        if (flaskUrl.isNotEmpty()) {
            apiManager.setFlaskBaseUrl(if (flaskUrl.endsWith("/")) flaskUrl else "$flaskUrl/")
        }
        
        if (nodejsUrl.isNotEmpty()) {
            apiManager.setNodejsBaseUrl(if (nodejsUrl.endsWith("/")) nodejsUrl else "$nodejsUrl/")
        }
        
        apiManager.setActiveBackend(activeBackend)
        
        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        
        // Register SIM cards with the new active backend
        backendService.registerSimCards()
        
        // Recheck connectivity with new settings
        checkBackendConnectivity()
    }
    
    private fun setButtonsEnabled(enabled: Boolean) {
        testFlaskButton.isEnabled = enabled
        testNodejsButton.isEnabled = enabled
        saveButton.isEnabled = enabled
    }
}
