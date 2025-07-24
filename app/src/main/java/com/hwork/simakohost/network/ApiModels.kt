package com.hwork.simakohost.network

import com.google.gson.annotations.SerializedName

// Data classes for API communication
data class SmsMessage(
    @SerializedName("sim_id") val simId: String,
    @SerializedName("type") val type: String,
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String? = null,
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("metadata") val metadata: Map<String, Any>? = null
)

data class SimCard(
    @SerializedName("sim_id") val simId: String,
    @SerializedName("phone_number") val phoneNumber: String,
    @SerializedName("carrier") val carrier: String? = null,
    @SerializedName("is_active") val isActive: Boolean = true
)

data class ApiResponse<T>(
    @SerializedName("status") val status: String,
    @SerializedName("message_id") val messageId: String? = null,
    @SerializedName("received") val received: T? = null,
    @SerializedName("error") val error: String? = null
)

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("service") val service: String,
    @SerializedName("mongodb") val mongodb: String,
    @SerializedName("timestamp") val timestamp: String
)
