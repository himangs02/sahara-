package com.yourteam.sahara.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted local verifier. This prototype does not provide server authentication or encrypted storage. */
class PasswordHasher(private val iterations: Int = 210_000) {
    data class Verifier(val hash: String, val salt: String, val algorithm: String, val iterations: Int)
    fun hash(password: String): Verifier {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.hex()
        val algorithm = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            "PBKDF2WithHmacSHA256"
        } catch (_: java.security.NoSuchAlgorithmException) { "PBKDF2WithHmacSHA1" }
        return Verifier(derive(password, salt, algorithm, iterations).hex(), salt, algorithm, iterations)
    }
    fun verify(password: String, account: AccountEntity): Boolean =
        MessageDigest.isEqual(
            derive(password, account.salt, account.algorithm, account.iterations),
            account.passwordHash.bytes()
        )
    private fun derive(password: String, salt: String, algorithm: String, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt.bytes(), rounds, 256)
        return try { SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun String.bytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
