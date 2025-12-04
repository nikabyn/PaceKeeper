package org.htwk.pacing.backend.data_collection.fitbit

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
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.random.Random
import kotlin.random.asKotlinRandom

class OAuth2Provider(
    val clientId: String,
    val authUri: Uri,
    val tokenUri: Uri,
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
                    Log.d("ktor", message)
                }
            }
            level = LogLevel.ALL
        }
    }

    private lateinit var codeVerifier: String
    private lateinit var state: String

    fun openLogin(context: Context, scopes: List<String>) {
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

    suspend fun onLoginResult(uri: Uri): TokenResponse {
        val authCode = uri.getQueryParameter("code") ?: error("Missing code")
        val newState = uri.getQueryParameter("state") ?: error("Missing state")

        if (state != newState) {
            error("State received from login is not the same")
        }

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
            error("$tokenUri returned: ${response.status}, ${response.body<String>()}")
        }

        return response.body<TokenResponse>()
    }

    @Serializable
    data class TokenResponse(
        @SerialName("access_token")
        val accessToken: String,
        @SerialName("refresh_token")
        val refreshToken: String,
        @SerialName("token_type")
        val tokenType: String,
        @SerialName("expires_in")
        val expiresIn: Int,
        val scope: String,
        @SerialName("user_id")
        val userId: String,
    )

    fun logout() {
        // TODO
    }
}

private fun randomString(random: Random, lengthRange: IntRange): String {
    val length = lengthRange.random(random)
    val allowedChars = ('A'..'Z') + ('a'..'z') + '-' + '.' + '_' + '~'

    return String(CharArray(length) { allowedChars.random(random) })
}

private fun String.sha256(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(toByteArray())