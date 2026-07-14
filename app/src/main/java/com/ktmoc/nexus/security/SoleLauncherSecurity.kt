package com.ktmoc.nexus.security

import android.content.Context
import android.util.Base64
import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * SoleLauncherSecurity - Ensures ONLY kingtravismoc can push updates
 * Handles method blocking/greenlighting and encrypted storage to I2PD
 */
class SoleLauncherSecurity(private val context: Context) {
    
    companion object {
        private const val TAG = "SoleLauncherSec"
        private const val SOLE_LAUNCHER_GITHUB = "kingtravismoc"
        private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        
        init {
            Security.addProvider(BouncyCastleProvider())
        }
    }
    
    // Encrypted storage of user-generated methods (serialized + encrypted)
    private val encryptedMethodStore = mutableMapOf<String, String>()
    
    // Region locking configuration
    private var allowedRegions = setOf("US", "EU", "GLOBAL")
    private var bannedUsers = mutableSetOf<String>()
    
    // Blockchain-like ledger for audit trail (simplified for I2PD storage)
    private val auditLedger = mutableListOf<AuditEntry>()
    
    /**
     * Verify GitHub Actions remote command is from sole launcher
     */
    fun verifyGitHubCommand(
        signerUsername: String,
        signature: String,
        payload: String,
        publicKey: String
    ): Boolean {
        return try {
            // Only allow kingtravismoc
            if (signerUsername != SOLE_LAUNCHER_GITHUB) {
                Log.e(TAG, "Unauthorized user attempt: $signerUsername")
                addToAuditLog("UNAUTHORIZED_ACCESS", signerUsername, false)
                return false
            }
            
            // Verify cryptographic signature
            val keyFactory = KeyFactory.getInstance("RSA", "BC")
            val publicKeySpec = PKCS8EncodedKeySpec(Base64.decode(publicKey, Base64.NO_WRAP))
            val pubKey = keyFactory.generatePublic(publicKeySpec)
            
            val signatureInstance = Signature.getInstance("SHA256withRSA", "BC")
            signatureInstance.initVerify(pubKey)
            signatureInstance.update(payload.toByteArray())
            
            val isValid = signatureInstance.verify(Base64.decode(signature, Base64.NO_WRAP))
            
            if (isValid) {
                addToAuditLog("COMMAND_VERIFIED", signerUsername, true)
                Log.i(TAG, "✓ Command verified from sole launcher: $SOLE_LAUNCHER_GITHUB")
            } else {
                addToAuditLog("SIGNATURE_INVALID", signerUsername, false)
                Log.e(TAG, "✗ Invalid signature")
            }
            
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Verification error: ${e.message}")
            addToAuditLog("VERIFICATION_ERROR", signerUsername, false, e.message)
            false
        }
    }
    
    /**
     * Greenlight a method (allow execution)
     */
    fun greenlightMethod(methodId: String, methodName: String, approvedBy: String) {
        if (approvedBy != SOLE_LAUNCHER_GITHUB) {
            throw SecurityException("Only $SOLE_LAUNCHER_GITHUB can greenlight methods")
        }
        
        addToAuditLog("METHOD_GREENLIGHTED", methodName, true, "Method ID: $methodId")
        Log.i(TAG, "✓ Method greenlighted: $methodName ($methodId)")
    }
    
    /**
     * Block a method (prevent execution)
     */
    fun blockMethod(methodId: String, methodName: String, reason: String, blockedBy: String) {
        if (blockedBy != SOLE_LAUNCHER_GITHUB) {
            throw SecurityException("Only $SOLE_LAUNCHER_GITHUB can block methods")
        }
        
        addToAuditLog("METHOD_BLOCKED", methodName, false, "Reason: $reason")
        Log.w(TAG, "⛔ Method blocked: $methodName - $reason")
    }
    
    /**
     * Store user-generated method as serialized encrypted string to I2PD
     */
    fun storeEncryptedMethod(
        methodId: String,
        methodCode: String,
        encryptionSecret: String,
        targetI2pdDestination: String
    ): String {
        try {
            // Serialize method code
            val serialized = serializeMethod(methodCode)
            
            // Encrypt with AES-GCM
            val encrypted = encryptWithAES(serialized, encryptionSecret)
            
            // Store locally and prepare for I2PD sync
            encryptedMethodStore[methodId] = encrypted
            
            // Create I2PD packet
            val i2pdPacket = createI2PDPacket(methodId, encrypted, targetI2pdDestination)
            
            addToAuditLog("METHOD_STORED_ENCRYPTED", methodId, true, "Target: $targetI2pdDestination")
            Log.i(TAG, "✓ Method stored encrypted: $methodId -> $targetI2pdDestination")
            
            return i2pdPacket
        } catch (e: Exception) {
            Log.e(TAG, "Encryption/storage failed: ${e.message}")
            addToAuditLog("METHOD_STORAGE_FAILED", methodId, false, e.message)
            throw e
        }
    }
    
    /**
     * Region locking check
     */
    fun isRegionAllowed(userRegion: String): Boolean {
        val allowed = allowedRegions.contains(userRegion) || allowedRegions.contains("GLOBAL")
        if (!allowed) {
            addToAuditLog("REGION_BLOCKED", userRegion, false)
            Log.w(TAG, "⛔ Region blocked: $userRegion")
        }
        return allowed
    }
    
    /**
     * Ban a user account
     */
    fun banUser(userId: String, reason: String, bannedBy: String) {
        if (bannedBy != SOLE_LAUNCHER_GITHUB) {
            throw SecurityException("Only $SOLE_LAUNCHER_GITHUB can ban users")
        }
        
        bannedUsers.add(userId)
        addToAuditLog("USER_BANNED", userId, false, "Reason: $reason")
        Log.w(TAG, "⛔ User banned: $userId - $reason")
    }
    
    /**
     * Check if user is banned
     */
    fun isUserBanned(userId: String): Boolean {
        return bannedUsers.contains(userId)
    }
    
    /**
     * Create user account on I2PD blockchain
     */
    fun createUserAccount(
        username: String,
        i2pdDestination: String,
        publicKey: String
    ): UserAccount {
        val account = UserAccount(
            id = generateAccountId(username),
            username = username,
            i2pdDestination = i2pdDestination,
            publicKey = publicKey,
            createdAt = System.currentTimeMillis(),
            createdBy = SOLE_LAUNCHER_GITHUB
        )
        
        // Add to blockchain ledger
        addToLedger(BlockchainEntry(
            type = "USER_CREATED",
            data = account.toJson(),
            timestamp = System.currentTimeMillis(),
            previousHash = auditLedger.lastOrNull()?.hash ?: "0"
        ))
        
        Log.i(TAG, "✓ User account created: ${account.username} (${account.id})")
        return account
    }
    
    /**
     * Real-time interrogation of I2PD storage systems
     */
    suspend fun interrogateI2PDStorage(
        destination: String,
        query: String
    ): I2PDQueryResult {
        addToAuditLog("I2PD_INTERROGATION", destination, true, "Query: $query")
        
        // Simulate real-time network interrogation
        val result = I2PDQueryResult(
            destination = destination,
            query = query,
            timestamp = System.currentTimeMillis(),
            status = "SUCCESS",
            data = performI2PDQuery(destination, query)
        )
        
        Log.i(TAG, "✓ I2PD interrogation complete: $destination")
        return result
    }
    
    // Private helper methods
    
    private fun serializeMethod(code: String): String {
        // Simple serialization (in production, use proper serialization)
        return Base64.encodeToString(code.toByteArray(), Base64.NO_WRAP)
    }
    
    private fun encryptWithAES(data: String, secret: String): String {
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM, "BC")
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
        val keySpec = SecretKeySpec(keyBytes, "AES")
        
        val iv = ByteArray(12) { (Math.random() * 256).toByte() }
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(data.toByteArray())
        
        // Combine IV + encrypted data
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
    
    private fun createI2PDPacket(methodId: String, encryptedData: String, destination: String): String {
        // I2PD packet format
        return """
            {
                "type": "ENCRYPTED_METHOD",
                "id": "$methodId",
                "destination": "$destination",
                "payload": "$encryptedData",
                "timestamp": ${System.currentTimeMillis()},
                "signature": "${generatePacketSignature(methodId, encryptedData)}"
            }
        """.trimIndent()
    }
    
    private fun generatePacketSignature(methodId: String, data: String): String {
        // Generate HMAC signature
        val mac = Mac.getInstance("HmacSHA256", "BC")
        val keySpec = SecretKeySpec(SOLE_LAUNCHER_GITHUB.toByteArray(), "HmacSHA256")
        mac.init(keySpec)
        val signature = mac.doFinal("$methodId$data".toByteArray())
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }
    
    private fun generateAccountId(username: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(
            "$username${System.currentTimeMillis()}".toByteArray()
        )
        return hash.joinToString("") { "%02x".format(it) }.take(16)
    }
    
    private fun addToAuditLog(action: String, target: String, success: Boolean, details: String? = null) {
        val entry = AuditEntry(
            action = action,
            target = target,
            success = success,
            details = details,
            timestamp = System.currentTimeMillis(),
            actor = SOLE_LAUNCHER_GITHUB
        )
        auditLedger.add(entry)
    }
    
    private fun addToLedger(entry: BlockchainEntry) {
        // Simplified blockchain implementation
        auditLedger.add(entry)
    }
    
    private fun performI2PDQuery(destination: String, query: String): String {
        // Placeholder for actual I2PD network query
        return "I2PD_QUERY_RESULT_FOR_$query"
    }
}

// Data classes

data class AuditEntry(
    val action: String,
    val target: String,
    val success: Boolean,
    val details: String?,
    val timestamp: Long,
    val actor: String
)

data class BlockchainEntry(
    val type: String,
    val data: String,
    val timestamp: Long,
    val previousHash: String,
    val hash: String = calculateHash(type, data, timestamp, previousHash)
) {
    companion object {
        private fun calculateHash(type: String, data: String, timestamp: Long, previousHash: String): String {
            val content = "$type$data$timestamp$previousHash"
            val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

data class UserAccount(
    val id: String,
    val username: String,
    val i2pdDestination: String,
    val publicKey: String,
    val createdAt: Long,
    val createdBy: String
) {
    fun toJson(): String {
        return """
            {
                "id": "$id",
                "username": "$username",
                "i2pdDestination": "$i2pdDestination",
                "publicKey": "$publicKey",
                "createdAt": $createdAt,
                "createdBy": "$createdBy"
            }
        """.trimIndent()
    }
}

data class I2PDQueryResult(
    val destination: String,
    val query: String,
    val timestamp: Long,
    val status: String,
    val data: String
)
