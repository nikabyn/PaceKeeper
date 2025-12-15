package org.htwk.pacing.backend.data_collection.fitbit

import androidx.core.net.toUri
import org.htwk.pacing.backend.OAuth2Provider

object Fitbit {
    const val TAG = "fitbit"

    private const val CLIENT_ID = "23TLPD"
    private val authUri = "https://www.fitbit.com/oauth2/authorize".toUri()
    private val tokenUri = "https://api.fitbit.com/oauth2/token".toUri()
    private val revokeUri = "https://api.fitbit.com/oauth2/revoke".toUri()
    val redirectUri = "org.htwk.pacing://fitbit_oauth2_redirect".toUri()


    val oAuth2Provider = OAuth2Provider(CLIENT_ID, authUri, tokenUri, revokeUri, redirectUri)
}