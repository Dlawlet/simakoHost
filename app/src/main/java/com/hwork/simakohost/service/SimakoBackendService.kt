package com.hwork.simakohost.service

import android.content.Context
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.hwork.simakohost.network.BackendApiManager
import com.hwork.simakohost.network.SmsMessage
import com.hwork.simakohost.network.SimCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SimakoBackendService private constructor(private val context: Context) {// this service is a singleton that handles communication with the Simako backend
    
    companion object {
        private const val TAG = "SimakoBackendService"
        
        @Volatile
        private var INSTANCE: SimakoBackendService? = null

        fun getInstance(context: Context): SimakoBackendService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SimakoBackendService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val apiManager = BackendApiManager.getInstance(context)
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    /**
     * Send SMS data to backend
     */
    fun sendSmsToBackend(
        sender: String,
        recipient: String?,
        messageBody: String,
        timestamp: Long,
        isIncoming: Boolean = true
    ) {
        serviceScope.launch {
            try {
                val simId = getCurrentSimId()
                val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(timestamp))
                
                val smsMessage = SmsMessage(
                    simId = simId,
                    type = "sms",
                    from = sender,
                    to = recipient,
                    message = messageBody,
                    timestamp = isoTimestamp,
                    metadata = mapOf(
                        "is_incoming" to isIncoming,
                        "app_version" to "1.0",
                        "device_info" to getDeviceInfo()
                    )
                )
                
                // Try sending to backend with automatic fallback
                val response = apiManager.executeWithFallback { api ->
                    api.sendMessage(smsMessage)
                }
                
                if (response?.isSuccessful == true) {
                    Log.d(TAG, "SMS sent to backend successfully: ${response.body()}")
                } else {
                    Log.e(TAG, "Failed to send SMS to any backend")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending SMS to backend: ${e.message}", e)
            }
        }
    }
    
    /**
     * Send call data to backend
     */
    fun sendCallToBackend(
        caller: String,
        recipient: String?,
        duration: Long,
        timestamp: Long,
        callType: String // "incoming", "outgoing", "missed"
    ) {
        serviceScope.launch {
            try {
                val simId = getCurrentSimId()
                val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(timestamp))
                
                val callMessage = SmsMessage(
                    simId = simId,
                    type = "call",
                    from = caller,
                    to = recipient,
                    message = "Call - Duration: ${duration}s, Type: $callType",
                    timestamp = isoTimestamp,
                    metadata = mapOf(
                        "call_type" to callType,
                        "duration_seconds" to duration,
                        "app_version" to "1.0",
                        "device_info" to getDeviceInfo()
                    )
                )
                
                // Try sending to backend with automatic fallback
                val response = apiManager.executeWithFallback { api ->
                    api.sendMessage(callMessage)
                }
                
                if (response?.isSuccessful == true) {
                    Log.d(TAG, "Call data sent to backend successfully: ${response.body()}")
                } else {
                    Log.e(TAG, "Failed to send call data to any backend")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending call data to backend: ${e.message}", e)
            }
        }
    }
    
    /**
     * Register current SIM card(s) with backend
     */
    fun registerSimCards() {
        serviceScope.launch {
            try {
                val simCards = getAvailableSimCards()
                
                simCards.forEach { simCard ->
                    try {
                        // Try registering with automatic fallback
                        val response = apiManager.executeWithFallback { api ->
                            api.registerSimCard(simCard)
                        }
                        
                        if (response?.isSuccessful == true) {
                            Log.d(TAG, "SIM card registered successfully: ${simCard.simId}")
                        } else {
                            Log.e(TAG, "Failed to register SIM card on any backend: ${simCard.simId}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error registering SIM card ${simCard.simId}: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error getting SIM cards: ${e.message}", e)
            }
        }
    }
    
    /**
     * Check backend connectivity
     */
    fun checkBackendConnectivity(callback: (Map<String, Boolean>) -> Unit) {
        serviceScope.launch {
            try {
                val healthStatus = apiManager.checkBackendHealth()
                callback(healthStatus)
                Log.e(TAG, "apimanager on point")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking backend connectivity: ${e.message}", e)
                callback(mapOf("flask" to false, "nodejs" to false))
            }
        }
    }
    
    private fun getCurrentSimId(): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val simSerialNumber = telephonyManager.simSerialNumber
            simSerialNumber ?: "SIM_UNKNOWN_${System.currentTimeMillis()}"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SIM ID: ${e.message}")
            "SIM_UNKNOWN_${System.currentTimeMillis()}"
        }
    }
    
    private fun getAvailableSimCards(): List<SimCard> {
        val simCards = mutableListOf<SimCard>()
        
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                
                activeSubscriptions?.forEach { subscriptionInfo ->
                    try {
                        val simCard = SimCard(
                            simId = subscriptionInfo.iccId ?: "SIM_${subscriptionInfo.subscriptionId}",
                            phoneNumber = subscriptionInfo.number ?: "Unknown",
                            carrier = subscriptionInfo.carrierName?.toString() ?: "Unknown Carrier",
                            isActive = true
                        )
                        simCards.add(simCard)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing subscription info: ${e.message}")
                    }
                }
            } else {
                // Fallback for older Android versions
                val simCard = SimCard(
                    simId = telephonyManager.simSerialNumber ?: "SIM_LEGACY",
                    phoneNumber = telephonyManager.line1Number ?: "Unknown",
                    carrier = telephonyManager.networkOperatorName ?: "Unknown Carrier",
                    isActive = true
                )
                simCards.add(simCard)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available SIM cards: ${e.message}")
            // Add a default SIM card if we can't get real info
            simCards.add(
                SimCard(
                    simId = "SIM_DEFAULT_${System.currentTimeMillis()}",
                    phoneNumber = "Unknown",
                    carrier = "Unknown Carrier",
                    isActive = true
                )
            )
        }
        
        return simCards
    }
    
    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "manufacturer" to android.os.Build.MANUFACTURER,
            "model" to android.os.Build.MODEL,
            "android_version" to android.os.Build.VERSION.RELEASE,
            "sdk_int" to android.os.Build.VERSION.SDK_INT.toString()
        )
    }
}
