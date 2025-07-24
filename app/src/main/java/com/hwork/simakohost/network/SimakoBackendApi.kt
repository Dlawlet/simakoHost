package com.hwork.simakohost.network

import retrofit2.Response
import retrofit2.http.*

interface SimakoBackendApi { // the purpose of this interface is to define the API endpoints for the Simako backend
    
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>
    
    @POST("api/messages")
    suspend fun sendMessage(@Body message: SmsMessage): Response<ApiResponse<SmsMessage>>
    
    @GET("api/messages")
    suspend fun getMessages(
        @Query("sim_id") simId: String? = null,
        @Query("type") type: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("skip") skip: Int? = null
    ): Response<ApiResponse<List<SmsMessage>>>
    
    @POST("api/sim-cards")
    suspend fun registerSimCard(@Body simCard: SimCard): Response<ApiResponse<SimCard>>
    
    @GET("api/sim-cards")
    suspend fun getSimCards(): Response<ApiResponse<List<SimCard>>>
    
    @PUT("api/messages/{messageId}/processed")
    suspend fun markMessageProcessed(@Path("messageId") messageId: String): Response<ApiResponse<Any>>
    
    @GET("api/simakohost/status")
    suspend fun getSimakoHostStatus(): Response<ApiResponse<Any>>
    
    @POST("api/simakohost/send-sms")
    suspend fun sendSmsViaSimakoHost(@Body request: Map<String, Any>): Response<ApiResponse<Any>>
}
