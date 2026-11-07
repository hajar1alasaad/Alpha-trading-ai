package com.alphatrade.ai.phantom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.alphatrade.ai.security.AndroidSecureVault
import com.iwebpp.crypto.TweetNaclFast
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Phantom Solana mobile deeplink flow.
 * Only stores this app's ephemeral X25519 secret, Phantom session and public wallet address.
 * It never asks for or stores a wallet seed phrase/private key.
 */
class PhantomDeepLinkManager(context: Context) {
    private val appContext = context.applicationContext
    private val vault = AndroidSecureVault(appContext)

    data class CallbackResult(
        val message: String,
        val connected: Boolean,
        val walletAddress: String? = null,
        val signatureVerified: Boolean = false
    )

    fun walletAddress(): String? = if (isConnected()) vault.get(KEY_WALLET_PUBLIC) else null

    fun disconnect() {
        listOf(
            KEY_DAPP_SECRET, KEY_DAPP_PUBLIC, KEY_PHANTOM_PUBLIC, KEY_SESSION,
            KEY_WALLET_PUBLIC, KEY_PENDING_ACTION, KEY_PENDING_MESSAGE, KEY_LAST_VERIFIED_SIGNATURE
        ).forEach(vault::remove)
    }

    fun isConnected(): Boolean = !vault.get(KEY_SESSION).isNullOrBlank() &&
        !vault.get(KEY_WALLET_PUBLIC).isNullOrBlank() &&
        !vault.get(KEY_PHANTOM_PUBLIC).isNullOrBlank()

    /** Starts a fresh Phantom connect session and returns the ACTION_VIEW intent. */
    fun beginConnect(appMetadataUrl: String): Intent {
        require(Uri.parse(appMetadataUrl).let { it.scheme == "https" && !it.host.isNullOrBlank() }) {
            "Set a valid public HTTPS app metadata URL before connecting Phantom"
        }
        val keyPair = TweetNaclFast.Box.keyPair()
        vault.put(KEY_DAPP_SECRET, Base64.encodeToString(keyPair.secretKey, Base64.NO_WRAP))
        vault.put(KEY_DAPP_PUBLIC, Base58.encode(keyPair.publicKey))
        vault.put(KEY_PENDING_ACTION, ACTION_CONNECT)
        vault.remove(KEY_PHANTOM_PUBLIC)
        vault.remove(KEY_SESSION)
        vault.remove(KEY_WALLET_PUBLIC)

        val redirect = REDIRECT_URI
        val uri = Uri.parse("https://phantom.com/ul/v1/connect").buildUpon()
            .appendQueryParameter("app_url", appMetadataUrl)
            .appendQueryParameter("dapp_encryption_public_key", Base58.encode(keyPair.publicKey))
            .appendQueryParameter("redirect_link", redirect)
            .appendQueryParameter("cluster", "mainnet-beta")
            .build()
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** Requests a human-readable, non-transaction signature to confirm wallet control. */
    fun beginSignInMessage(): Intent {
        check(isConnected()) { "Connect Phantom first" }
        val dappPublic = vault.get(KEY_DAPP_PUBLIC) ?: error("Missing app public key")
        val phantomPublic = Base58.decode(vault.get(KEY_PHANTOM_PUBLIC) ?: error("Missing Phantom key"))
        val dappSecret = decodeSecret(KEY_DAPP_SECRET)
        val session = vault.get(KEY_SESSION) ?: error("Missing Phantom session")
        val wallet = vault.get(KEY_WALLET_PUBLIC) ?: error("Missing wallet address")

        val challenge = Base58.encode(TweetNaclFast.randombytes(16))
        val time = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val message = "ALPHA TRADE AI sign-in\nWallet: $wallet\nNonce: $challenge\nIssued At: $time\nThis signature does not authorize any transaction."
        val payload = JSONObject()
            .put("message", Base58.encode(message.toByteArray(StandardCharsets.UTF_8)))
            .put("session", session)
            .put("display", "utf8")
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        val nonce = TweetNaclFast.makeBoxNonce()
        val box = TweetNaclFast.Box(phantomPublic, dappSecret)
        val encrypted = box.box(payload, nonce) ?: error("Unable to encrypt Phantom request")
        val uri = Uri.parse("https://phantom.com/ul/v1/signMessage").buildUpon()
            .appendQueryParameter("dapp_encryption_public_key", dappPublic)
            .appendQueryParameter("nonce", Base58.encode(nonce))
            .appendQueryParameter("redirect_link", REDIRECT_URI)
            .appendQueryParameter("payload", Base58.encode(encrypted))
            .build()
        vault.put(KEY_PENDING_ACTION, ACTION_SIGN_MESSAGE)
        vault.put(KEY_PENDING_MESSAGE, Base64.encodeToString(message.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP))
        return Intent(Intent.ACTION_VIEW, uri)
    }

    /** Processes only our explicit callback scheme and authenticates encrypted Phantom data. */
    fun handleCallback(uri: Uri?): CallbackResult? {
        if (uri == null || uri.scheme != "alphatradeai" || uri.host != "phantom-callback") return null
        val errorCode = uri.getQueryParameter("errorCode")
        if (!errorCode.isNullOrBlank()) {
            val errorMessage = uri.getQueryParameter("errorMessage") ?: "Phantom request rejected"
            vault.remove(KEY_PENDING_ACTION)
            vault.remove(KEY_PENDING_MESSAGE)
            return CallbackResult("رفض Phantom الطلب: $errorMessage", isConnected())
        }

        val pending = vault.get(KEY_PENDING_ACTION) ?: error("No pending Phantom request")
        val phantomPublicText = uri.getQueryParameter("phantom_encryption_public_key")
            ?: vault.get(KEY_PHANTOM_PUBLIC)
            ?: error("Missing Phantom encryption public key")
        val phantomPublic = Base58.decode(phantomPublicText)
        require(phantomPublic.size == TweetNaclFast.Box.publicKeyLength) { "Invalid Phantom public key length" }
        val nonce = Base58.decode(uri.getQueryParameter("nonce") ?: error("Missing Phantom nonce"))
        require(nonce.size == TweetNaclFast.Box.nonceLength) { "Invalid Phantom nonce length" }
        val encryptedData = Base58.decode(uri.getQueryParameter("data") ?: error("Missing encrypted Phantom response"))
        val decrypted = TweetNaclFast.Box(phantomPublic, decodeSecret(KEY_DAPP_SECRET)).open(encryptedData, nonce)
            ?: error("Phantom response authentication failed")
        val response = JSONObject(String(decrypted, StandardCharsets.UTF_8))

        return when (pending) {
            ACTION_CONNECT -> {
                val publicKey = response.getString("public_key")
                require(Base58.decode(publicKey).size == 32) { "Invalid Solana wallet public key" }
                val session = response.getString("session")
                require(session.isNotBlank()) { "Empty Phantom session" }
                vault.put(KEY_PHANTOM_PUBLIC, phantomPublicText)
                vault.put(KEY_SESSION, session)
                vault.put(KEY_WALLET_PUBLIC, publicKey)
                vault.remove(KEY_PENDING_ACTION)
                CallbackResult("تم الاتصال بمحفظة Phantom: ${shortAddress(publicKey)}", true, publicKey)
            }
            ACTION_SIGN_MESSAGE -> {
                val wallet = vault.get(KEY_WALLET_PUBLIC) ?: error("Wallet address missing")
                val signatureText = response.getString("signature")
                val signature = Base58.decode(signatureText)
                require(signature.size == 64) { "Invalid Ed25519 signature length" }
                val message = Base64.decode(vault.get(KEY_PENDING_MESSAGE) ?: error("Missing signed message"), Base64.NO_WRAP)
                val isValid = TweetNaclFast.Signature(Base58.decode(wallet), ByteArray(64))
                    .detached_verify(message, signature)
                require(isValid) { "Wallet signature verification failed" }
                vault.put(KEY_LAST_VERIFIED_SIGNATURE, signatureText)
                vault.remove(KEY_PENDING_ACTION)
                vault.remove(KEY_PENDING_MESSAGE)
                CallbackResult("تم التحقق من توقيع محفظتك بنجاح.", true, wallet, signatureVerified = true)
            }
            else -> error("Unknown Phantom callback action")
        }
    }

    private fun decodeSecret(key: String): ByteArray {
        val raw = Base64.decode(vault.get(key) ?: error("Missing encrypted app session key"), Base64.NO_WRAP)
        require(raw.size == TweetNaclFast.Box.secretKeyLength) { "Invalid app session secret key" }
        return raw
    }

    private fun shortAddress(address: String): String =
        if (address.length > 12) address.take(5) + "…" + address.takeLast(5) else address

    companion object {
        const val REDIRECT_URI = "alphatradeai://phantom-callback"
        private const val KEY_DAPP_SECRET = "phantom_dapp_secret_v1"
        private const val KEY_DAPP_PUBLIC = "phantom_dapp_public_v1"
        private const val KEY_PHANTOM_PUBLIC = "phantom_peer_public_v1"
        private const val KEY_SESSION = "phantom_session_v1"
        private const val KEY_WALLET_PUBLIC = "phantom_wallet_public_v1"
        private const val KEY_PENDING_ACTION = "phantom_pending_action_v1"
        private const val KEY_PENDING_MESSAGE = "phantom_pending_message_v1"
        private const val KEY_LAST_VERIFIED_SIGNATURE = "phantom_last_signature_v1"
        private const val ACTION_CONNECT = "connect"
        private const val ACTION_SIGN_MESSAGE = "signMessage"
    }
}
