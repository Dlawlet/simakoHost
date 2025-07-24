package com.hwork.simakohost.activities

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hwork.simakohost.AllConversationAdapter
import com.hwork.simakohost.R
import com.hwork.simakohost.SMS
import com.hwork.simakohost.utils.MainActivityMVC
import com.hwork.simakohost.service.SimakoBackendService

class MainActivity : AppCompatActivity(),
    View.OnClickListener,
    LoaderManager.LoaderCallbacks<Cursor?>,
    SearchView.OnQueryTextListener{

    companion object {
        private const val TAG = "MainActivity-SMS"
        private var mCurFilter: String? = null
        private var data: ArrayList<SMS>? = null
        private var mReceiver: BroadcastReceiver? = null
        private lateinit var recyclerView: RecyclerView
        private var smsContentObserver: ContentObserver? = null

    }

    lateinit var mainActivityMVC: MainActivityMVC
    private var allConversationAdapter: AllConversationAdapter? = null
    private lateinit var backendService: SimakoBackendService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🚀 MainActivity onCreate started")
        this.initializeApp()
    }

    override fun onPause() {
        super.onPause()
        mReceiver?.let {
            unregisterReceiver(it)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "MainActivity onResume called")
        // Check if we're the default SMS app and request if not
        checkAndRequestDefaultSmsApp()

        mainActivityMVC.checkDefault()
        val intentFilter = IntentFilter("com.hwork.simakohost.NEW_SMS")
        mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.d(TAG, "Broadcast received in MainActivity")
                val new_sms = intent.getBooleanExtra("new_sms", false)
                if (new_sms) {
                    Log.d(TAG, "New SMS detected, restarting loader")
                    // Add a small delay to ensure SMS is saved to content provider
                    Handler(Looper.getMainLooper()).postDelayed({
                        supportLoaderManager.restartLoader(123, null, this@MainActivity)
                    }, 1000) // Increased delay to 1 second
                }
            }
        }
        this.registerReceiver(mReceiver, intentFilter)
        Log.d(TAG, "Registered broadcast receiver for NEW_SMS")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure receiver is unregistered to prevent memory leaks
        try {
            mReceiver?.let { unregisterReceiver(it) }
        } catch (e: IllegalArgumentException) {
            // Receiver was already unregistered
        }

        // Unregister content observer
        smsContentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
    }

    override fun onRestart() {
        super.onRestart()
        mainActivityMVC.checkDefault()
    }

    override fun onClick(p0: View?) {
        // Handle click events if needed
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor?> {
        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.READ,
            Telephony.Sms.DATE
        )
        return CursorLoader(this, uri, projection, null, null, "${Telephony.Sms.DATE} DESC")
    }

    override fun onLoaderReset(loader: Loader<Cursor?>) {
        data?.clear()
        allConversationAdapter?.notifyDataSetChanged()
    }

    override fun onLoadFinished(loader: Loader<Cursor?>, cursor: Cursor?) {
        Log.d(TAG, "onLoadFinished called")
        data?.clear()
        cursor?.let {
            Log.d(TAG, "Cursor has ${it.count} SMS messages")
            if (it.moveToFirst()) {
                do {
                    try {
                        val sms = SMS().apply {
                            id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                            address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                            msg = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                            readState = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.READ))
                            time = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                        }
                        data?.add(sms)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading SMS: ${e.message}")
                        // Skip this SMS if there's an error reading it
                        continue
                    }
                } while (it.moveToNext())
            }
        }
        Log.d(TAG, "Loaded ${data?.size} SMS messages")
        allConversationAdapter?.notifyDataSetChanged()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        // Handle search submission TODO
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        // Handle search text changes, TODO
        return false
    }


    private fun initializeApp() {
        try {
            mainActivityMVC = MainActivityMVC(LayoutInflater.from(this), null, this)
            setContentView(mainActivityMVC.getRootView_())

            // Initialize Simako Backend Service
            backendService = SimakoBackendService.getInstance(this)

            // Check if we're the default SMS app and request if not
            checkAndRequestDefaultSmsApp()

            // Check backend connectivity and register SIM cards
            initializeBackendConnection()

            // Check if we're the default SMS app
            val isDefault = Telephony.Sms.getDefaultSmsPackage(this) == packageName
            Log.d(TAG, "Is default SMS app: $isDefault")

            // Initialize RecyclerView
            recyclerView = findViewById(R.id.recyclerview)
            recyclerView.layoutManager = LinearLayoutManager(this)
            
            // Initialize data and adapter
            data = ArrayList()
            allConversationAdapter = AllConversationAdapter(this, data)
            recyclerView.adapter = allConversationAdapter
            
            mainActivityMVC.checkDefault()
            mainActivityMVC.setListeners()
            
            // Set up SMS content observer to watch for database changes
            smsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d(TAG, "SMS database changed, refreshing...")
                    Handler(Looper.getMainLooper()).postDelayed({
                        supportLoaderManager.restartLoader(123, null, this@MainActivity)
                    }, 500)
                }
            }
            
            contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsContentObserver!!)
            
            // Start loading SMS data
            supportLoaderManager.initLoader(123, null, this)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeApp: ${e.message}", e)
            throw e // Re-throw to be caught by onCreate
        }
    }
    
    private fun initializeBackendConnection() {
        Log.d(TAG, "Initializing Simako backend connection...")
        
        // Check backend connectivity
        backendService.checkBackendConnectivity { healthStatus ->
            runOnUiThread {
                val flaskStatus = healthStatus["flask"] ?: false
                val nodejsStatus = healthStatus["nodejs"] ?: false
                
                Log.d(TAG, "Backend health check - Flask: $flaskStatus, Node.js: $nodejsStatus")
                
                if (flaskStatus || nodejsStatus) {
                    Log.d(TAG, "Backend connection successful, registering SIM cards...")
                    // Register SIM cards with backend
                    backendService.registerSimCards()
                } else {
                    Log.w(TAG, "No backend connection available")
                }
            }
        }
    }

    // Default SMS app handling
    private fun checkAndRequestDefaultSmsApp() {
        try {
            val currentDefault = Telephony.Sms.getDefaultSmsPackage(this)
            val isDefault = currentDefault == packageName
            
            Log.d(TAG, "Current default SMS app: $currentDefault")
            Log.d(TAG, "Is this app default: $isDefault")
            
            if (!isDefault) {
                Log.d(TAG, "Requesting to become default SMS app...")
                requestDefaultSmsApp()
            } else {
                Log.d(TAG, "✅ Already the default SMS app")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default SMS app: ${e.message}", e)
        }
    }
    
    private fun requestDefaultSmsApp() {
        try {
            val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivity(intent)
            Log.d(TAG, "✅ Already the default SMS app v2")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request default SMS app: ${e.message}")
            // Show manual instruction dialog
            showDefaultSmsInstructions()
        }
    }
    
    private fun showDefaultSmsInstructions() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Set as Default SMS App")
            .setMessage("To receive SMS messages, please set this app as your default SMS app:\n\n" +
                    "Method 1: Automatic\n" +
                    "• Tap 'Try Again' to open the default apps selector\n\n" +
                    "Method 2: Manual\n" +
                    "• Go to Settings > Apps > Default apps\n" +
                    "• Find 'SMS app' and select it\n" +
                    "• Choose 'Simako Host' from the list\n\n" +
                    "Method 3: Role Manager\n" +
                    "• Go to Settings > Apps & notifications > Special app access > SMS\n" +
                    "• Select 'Simako Host'")
            .setPositiveButton("Try Again") { _, _ ->
                requestDefaultSmsApp()
            }
            .setNeutralButton("Open Settings") { _, _ ->
                try {
                    // Try to open SMS app settings directly
                    val intent = Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS")
                    startActivity(intent)
                } catch (e1: Exception) {
                    try {
                        // Fallback to general settings
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to open settings: ${e2.message}")
                    }
                }
            }
            .setNegativeButton("Later") { _, _ -> }
            .show()
    }

}