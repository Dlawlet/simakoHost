package com.hwork.simakohost.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.hwork.simakohost.service.SimakoBackendService

class CallReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "CallReceiver-sima"
        private var lastState = TelephonyManager.CALL_STATE_IDLE
        private var callStartTime = 0L
        private var isIncoming = false
        private var number: String? = null
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            Log.d(TAG, "Phone state changed: $state, number: $incomingNumber")
            
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    // Incoming call ringing
                    isIncoming = true
                    number = incomingNumber
                    Log.d(TAG, "Incoming call from: $incomingNumber")
                }
                
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // Call answered/started
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        // Incoming call answered
                        callStartTime = System.currentTimeMillis()
                        Log.d(TAG, "Incoming call answered")
                    } else {
                        // Outgoing call started
                        isIncoming = false
                        callStartTime = System.currentTimeMillis()
                        Log.d(TAG, "Outgoing call started")
                    }
                }
                
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    // Call ended
                    if (lastState == TelephonyManager.CALL_STATE_OFFHOOK) {
                        // Call was active and now ended
                        val callDuration = if (callStartTime > 0) {
                            (System.currentTimeMillis() - callStartTime) / 1000
                        } else {
                            0L
                        }
                        
                        val callType = if (isIncoming) "incoming" else "outgoing"
                        Log.d(TAG, "Call ended - Type: $callType, Duration: ${callDuration}s")
                        
                        // Send call data to backend
                        context?.let { ctx ->
                            val backendService = SimakoBackendService.getInstance(ctx)
                            backendService.sendCallToBackend(
                                caller = if (isIncoming) number ?: "Unknown" else "Self",
                                recipient = if (isIncoming) null else number,
                                duration = callDuration,
                                timestamp = System.currentTimeMillis(),
                                callType = callType
                            )
                            Log.d(TAG, "Call data sent to Simako backend")
                        }
                        
                    } else if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        // Missed call
                        Log.d(TAG, "Missed call from: $number")
                        
                        context?.let { ctx ->
                            val backendService = SimakoBackendService.getInstance(ctx)
                            backendService.sendCallToBackend(
                                caller = number ?: "Unknown",
                                recipient = null,
                                duration = 0L,
                                timestamp = System.currentTimeMillis(),
                                callType = "missed"
                            )
                            Log.d(TAG, "Missed call data sent to Simako backend")
                        }
                    }
                    
                    // Reset call tracking
                    callStartTime = 0L
                    number = null
                }
            }
            
            // Update last state
            lastState = when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
                TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
                else -> TelephonyManager.CALL_STATE_IDLE
            }
        }
    }
}
