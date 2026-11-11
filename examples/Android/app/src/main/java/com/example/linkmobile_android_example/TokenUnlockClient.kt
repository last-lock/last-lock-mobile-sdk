package com.example.linkmobile_android_example

import android.util.Base64
import com.lastlock.mobile.sdk.ReaderGroupTrust
import com.lastlock.mobile.sdk.TokenUnlockPass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Calls the example business server to exchange a credential public key for an unlock pass. */
class TokenUnlockClient(
    private val businessServerUrl: String,
) {
    suspend fun issueTemporaryUnlockPass(credentialPublicKey: ByteArray): TokenUnlockPass =
        issueTokenUnlockPass(credentialPublicKey, 7 * 24 * 60 * 60)

    private suspend fun issueTokenUnlockPass(
        credentialPublicKey: ByteArray,
        duration: Int,
    ): TokenUnlockPass = withContext(Dispatchers.IO) {
        val url = URL("${businessServerUrl.trimEnd('/')}/v1/issue-unlock-pass")
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.doOutput = true

            val requestBody = JSONObject().apply {
                put(
                    "credential_public_key",
                    Base64.encodeToString(credentialPublicKey, Base64.NO_WRAP),
                )
                put("duration", duration)
            }.toString()
            connection.outputStream.use { output ->
                output.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseBody = (if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                throw IOException("Token-unlock pass request failed (HTTP $responseCode): $responseBody")
            }

            val response = IssueTokenUnlockPassResponse.fromJson(JSONObject(responseBody))
            TokenUnlockPass(
                accessDocument = Base64.decode(response.accessDocument, Base64.DEFAULT),
                readerGroups = response.readerGroups.map { group ->
                    ReaderGroupTrust(
                        readerGroupIdentifier = Base64.decode(group.readerGroupID, Base64.DEFAULT),
                        issuerCAPublicKey = Base64.decode(group.issuerCAPublicKey, Base64.DEFAULT),
                        issuerCACertificateDer = Base64.decode(
                            group.issuerCACertificateDER,
                            Base64.DEFAULT,
                        ),
                    )
                },
            )
        } finally {
            connection.disconnect()
        }
    }
}

private data class IssueTokenUnlockPassResponse(
    val accessDocument: String,
    val readerGroups: List<ReaderGroup>,
) {
    data class ReaderGroup(
        val readerGroupID: String,
        val issuerCAPublicKey: String,
        val issuerCACertificateDER: String,
    )

    companion object {
        fun fromJson(json: JSONObject): IssueTokenUnlockPassResponse {
            val groups = json.getJSONArray("reader_groups")
            return IssueTokenUnlockPassResponse(
                accessDocument = json.getString("access_document"),
                readerGroups = List(groups.length()) { index ->
                    val group = groups.getJSONObject(index)
                    ReaderGroup(
                        readerGroupID = group.getString("reader_group_id"),
                        issuerCAPublicKey = group.getString("issuer_ca_public_key"),
                        issuerCACertificateDER = group.getString("issuer_ca_certificate_der"),
                    )
                },
            )
        }
    }
}
