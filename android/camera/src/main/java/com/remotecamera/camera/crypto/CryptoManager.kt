package com.remotecamera.camera.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec

/**
 * Robust device identity manager using Android KeyStore with software fallback for maximum device compatibility.
 */
class CryptoManager {

    companion object {
        private const val KEY_ALIAS = "device_identity_key_pair"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    private var fallbackKeyPair: KeyPair? = null

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKeystoreKeyPair()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to software EC key generation if hardware KeyStore fails on device
            try {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(256)
                fallbackKeyPair = kpg.generateKeyPair()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun generateKeystoreKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
        }

        keyPairGenerator.initialize(specBuilder.build())
        return keyPairGenerator.generateKeyPair()
    }

    fun getPublicKeyBase64(): String {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val certificate = keyStore.getCertificate(KEY_ALIAS)
            if (certificate != null) {
                Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP)
            } else if (fallbackKeyPair != null) {
                Base64.encodeToString(fallbackKeyPair!!.public.encoded, Base64.NO_WRAP)
            } else {
                ""
            }
        } catch (e: Exception) {
            fallbackKeyPair?.let { Base64.encodeToString(it.public.encoded, Base64.NO_WRAP) } ?: ""
        }
    }

    fun signData(data: ByteArray): String {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            val privateKey = entry?.privateKey ?: fallbackKeyPair?.private
            ?: return ""

            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(data)
            Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun verifySignature(data: ByteArray, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
            val publicKey: PublicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }
}
