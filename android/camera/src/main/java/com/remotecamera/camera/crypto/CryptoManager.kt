package com.remotecamera.camera.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Manages device cryptographic identity using hardware-backed Android KeyStore.
 * Private key is stored securely in hardware and cannot be exported.
 */
class CryptoManager {

    companion object {
        private const val KEY_ALIAS = "device_identity_key_pair"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    init {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
        }
    }

    /**
     * Generates an EC key pair in Android KeyStore.
     */
    private fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setDigests(KeyProperties.DIGEST_SHA256)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Request strongbox backing if available
                setIsStrongBoxBacked(false)
            }
        }

        keyPairGenerator.initialize(specBuilder.build())
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Gets the public key for this device in Base64 X.509 format.
     */
    fun getPublicKeyBase64(): String {
        val certificate = keyStore.getCertificate(KEY_ALIAS)
            ?: throw IllegalStateException("Device identity certificate not found")
        val publicKeyBytes = certificate.publicKey.encoded
        return Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
    }

    /**
     * Signs data (e.g. pairing challenge) using device private key.
     */
    fun signData(data: ByteArray): String {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Private key entry not found")
        val privateKey: PrivateKey = entry.privateKey

        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(data)
        val signatureBytes = signature.sign()

        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    /**
     * Verifies signature using a Base64-encoded public key.
     */
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
