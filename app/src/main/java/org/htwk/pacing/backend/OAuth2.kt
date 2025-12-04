package org.htwk.pacing.backend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.random.Random
import kotlin.random.asKotlinRandom

class OAuth2Provider(
    val clientId: String,
    val authUri: Uri,
    val tokenUri: Uri,
    val revokeUri: Uri,
    val redirectUri: Uri,
) {
    // request auth code: (/auth)
    //   generate code verifier + code challenge
    //   request auth code + code challenge (start intent)
    //   catch redirect
    //   extract auth code

    // request access token: (/token)
    //   send auth code + code verifier
    //   receive id token + access token

    private val httpClient = HttpClient {
        expectSuccess = false
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.d("OAuth2($clientId)", message)
                }
            }
            level = LogLevel.INFO
        }
    }

    private lateinit var codeVerifier: String
    private lateinit var state: String

    fun startLogin(context: Context, scopes: List<String>) {
        val rng = SecureRandom().asKotlinRandom()
        codeVerifier = randomString(rng, 43..128)
        state = randomString(rng, 10..20)

        // hashed code verifier as Base64Url without padding
        val codeChallenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(codeVerifier.sha256())

        val uri = authUri.buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri.toString())
            .appendQueryParameter("scope", scopes.joinToString(separator = " "))
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()

        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun completeLogin(uri: Uri): OAuth2Result {
        val authCode = uri.getQueryParameter("code")
            ?: return OAuth2Result.RedirectUriError.MissingAuthCode
        val newState = uri.getQueryParameter("state")
            ?: return OAuth2Result.RedirectUriError.MissingState
        if (state != newState)
            return OAuth2Result.RedirectUriError.StateMismatch

        val response = httpClient.post(tokenUri.toString()) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append("client_id", clientId)
                    append("grant_type", "authorization_code")
                    append("code", authCode)
                    append("redirect_uri", redirectUri.toString())
                    append("code_verifier", codeVerifier)
                }.formUrlEncode()
            )
        }

        if (!response.status.isSuccess()) {
            return OAuth2Result.HttpError(response.status, response.body<String>())
        }

        return response.body<OAuth2Result.TokenResponse>()
    }

    /**
     * https://datatracker.ietf.org/doc/html/rfc7009
     *
     * @param token [access token][OAuth2Result.TokenResponse.accessToken] or [refresh token][OAuth2Result.TokenResponse.refreshToken] to be invalidated
     */
    suspend fun revoke(token: String): HttpResponse =
        httpClient.post(tokenUri.toString()) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append("client_id", clientId)
                    append("token", token)
                }.formUrlEncode()
            )
        }
}

sealed interface OAuth2Result {
    @Serializable
    @JsonIgnoreUnknownKeys
    @OptIn(ExperimentalSerializationApi::class)
    data class TokenResponse(
        /** Required */
        @SerialName("access_token")
        val accessToken: String,

        /** Required */
        @SerialName("token_type")
        val tokenType: String,

        /** Recommended: Time in seconds until [accessToken] expires */
        @SerialName("expires_in")
        val expiresIn: Int? = null,

        /** Optional: Token used to refresh [accessToken] */
        @SerialName("refresh_token")
        val refreshToken: String? = null,

        /** Optional: Space separated list of scopes */
        val scope: String? = null,
    ) : OAuth2Result

    enum class RedirectUriError : OAuth2Result {
        MissingAuthCode,
        MissingState,
        StateMismatch,
    }

    data class HttpError(
        val status: HttpStatusCode,
        val body: String,
    ) : OAuth2Result
}

private fun randomString(random: Random, lengthRange: IntRange): String {
    val length = lengthRange.random(random)
    val allowedChars = ('A'..'Z') + ('a'..'z') + '-' + '.' + '_' + '~'

    return String(CharArray(length) { allowedChars.random(random) })
}

private fun String.sha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(toByteArray())