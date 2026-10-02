package io.github.pini236.skiapp.account

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.pini236.skiapp.group.ApiException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * "Continue with Google" (docs/USERS.md: on Android, Google only): the system's own sheet with the accounts already
 * on the phone (Credential Manager), no password and nothing typed. It returns Google's ID token for the server
 * (Supabase signs in with it, server/README.md); the raw nonce goes with it, Google gets only its hash.
 */
object GoogleSignIn {
    /**
     * The web client id from Google Cloud (Pini creates it; docs/APP-NATIVE.md). Not a secret: it is in every app that
     * signs in with Google. Empty until it exists, and then the button says Google sign-in is not ready yet.
     */
    const val WEB_CLIENT_ID = ""

    class Result(val idToken: String, val nonce: String, val name: String?)

    suspend fun signIn(activity: Activity): Result {
        if (WEB_CLIENT_ID.isBlank()) throw ApiException("google_not_ready")
        val nonce = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val hashed = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()).joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).setNonce(hashed).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = try {
            CredentialManager.create(activity).getCredential(activity, request)
        } catch (e: GetCredentialCancellationException) {
            throw ApiException("cancelled")
        } catch (e: GetCredentialException) {
            throw ApiException("google_failed")
        }
        val c = response.credential
        if (c !is CustomCredential || c.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) throw ApiException("google_failed")
        val g = GoogleIdTokenCredential.createFrom(c.data)
        return Result(g.idToken, nonce, g.displayName)
    }
}
