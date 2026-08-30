package hq.playfoundry.questgrow.data.net

import hq.playfoundry.questgrow.core.ApiResult
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Supplies the bearer token for the *current* scope (parent or child). */
fun interface TokenProvider {
    fun current(): String?
}

internal val questGrowJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

object ApiClientFactory {

    fun create(baseUrl: String, tokenProvider: TokenProvider): QuestGrowApi {
        val auth = Interceptor { chain ->
            val req = chain.request()
            val token = tokenProvider.current()
            val out = if (token != null && req.header("Authorization") == null) {
                req.newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                req
            }
            chain.proceed(out)
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(questGrowJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(QuestGrowApi::class.java)
    }
}

/**
 * Runs a Retrofit call and folds it into [ApiResult]:
 *  * 2xx            → [ApiResult.Ok]
 *  * 4xx/5xx        → [ApiResult.Failure] with the backend `{detail, code}`
 *  * no response    → [ApiResult.Offline]  (never confused with a real failure)
 */
suspend fun <T> apiCall(block: suspend () -> Response<T>): ApiResult<T> = try {
    val resp = block()
    val body = resp.body()
    if (resp.isSuccessful && body != null) {
        ApiResult.Ok(body)
    } else if (resp.isSuccessful) {
        @Suppress("UNCHECKED_CAST")
        ApiResult.Ok(Unit as T)
    } else {
        val raw = resp.errorBody()?.string().orEmpty()
        val err = runCatching { questGrowJson.decodeFromString(ApiError.serializer(), raw) }
            .getOrDefault(ApiError(detail = raw.take(200), code = "error"))
        ApiResult.Failure(status = resp.code(), code = err.code, detail = err.detail)
    }
} catch (e: IOException) {
    ApiResult.Offline(e)
} catch (e: Exception) {
    ApiResult.Failure(status = -1, code = "client_error", detail = e.message ?: e.toString())
}
