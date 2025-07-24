package com.hwork.simakohost.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.hwork.simakohost.service.SimakoBackendService

class SMSReceiver :  BroadcastReceiver(){
    companion object {
        private const val TAG = "SmsReceiver-sima"

        fun parseSmsMessages(intent: Intent?): List<SmsMessage> {
            val bundle: Bundle? = intent?.extras
            val format = intent?.getStringExtra("format")
            val pdus = bundle?.get("pdus") as? Array<*> ?: return emptyList()
            return pdus.mapNotNull { pdu ->
                try {
                    SmsMessage.createFromPdu(pdu as ByteArray, format)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d(TAG, "SMS received - Action: ${intent?.action}")
        when (intent?.action) {
            "android.provider.Telephony.SMS_DELIVER" -> {
                Log.d(TAG, "Processing SMS_DELIVER")
                val messages = parseSmsMessages(intent)
                messages.forEach { message ->
                    val sender = message.originatingAddress
                    val body = message.messageBody
                    val timestamp = message.timestampMillis
                    Log.d(TAG, "SMS delivered from: $sender, Message: $body")

                    // Save SMS to system database
                    saveSmsToDatabase(context, sender, body, timestamp)

                    // Send SMS  to backend
                    context?.let { ctx ->
                        val backendService = SimakoBackendService.getInstance(ctx)
                        backendService.sendSmsToBackend(
                            sender = sender ?: "Unknown",
                            recipient = null,
                            messageBody = body ?: "",
                            timestamp = timestamp,
                            isIncoming = true
                        )
                        Log.d(TAG, "SMS delivery data sent to Simako backend")
                    }
                }
                // Notify MainActivity to refresh SMS list
                notifyMainActivity(context)
            }
            else -> {
                Log.d(TAG, "Unknown SMS action: ${intent?.action}")
            }
        }
    }
    
    private fun saveSmsToDatabase(context: Context?, sender: String?, body: String?, timestamp: Long) {
        if (context == null || sender == null || body == null) return
        
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, sender)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, 0) // Mark as unread
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.THREAD_ID, getThreadId(context, sender))
            }
            
            val uri = context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            if (uri != null) {
                Log.d(TAG, "SMS saved to database successfully: $uri")
            } else {
                Log.e(TAG, "Failed to save SMS to database")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving SMS to database: ${e.message}")
        }
    }
    
    private fun getThreadId(context: Context, address: String): Long {
        return try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting thread ID: ${e.message}")
            0L
        }
    }
    
    private fun notifyMainActivity(context: Context?) {
        Log.d(TAG, "About to send broadcast to MainActivity")
        val refreshIntent = Intent("com.hwork.simakohost.NEW_SMS")
        refreshIntent.putExtra("new_sms", true)
        refreshIntent.setPackage("com.hwork.simakohost") // Ensure it goes to our app only
        context?.sendBroadcast(refreshIntent)
        Log.d(TAG, "Sent broadcast to refresh MainActivity with package name")
    }
    }
