package com.ktmoc.nexus.usesof.memory

import android.util.Log
import com.ktmoc.nexus.usesof.kernel.ConceptKernel
import kotlin.math.exp
import kotlin.system.getTimeMillis

/**
 * Exponential Decay Memory for USESOF Framework
 * Implements: Memory(c) = Σₜ cₜ · e^(−λt)
 * Tracks repetition and escalation patterns
 * 
 * WATERMARKED FOR KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU
 * License: kingtravismo@gmail.com
 */
class ExponentialDecayMemory(
    private val decayLambda: Double = 0.1,
    private val escalationWindow: Int = 10
) {
    
    companion object {
        private const val TAG = "ExponentialDecayMemory"
    }
    
    /**
     * Memory entry for a concept occurrence
     */
    data class MemoryEntry(
        val conceptId: String,
        val severity: Double,
        val timestamp: Long,
        val context: String = ""
    )
    
    // Store memory entries by concept ID
    private val memoryStore = mutableMapOf<String, MutableList<MemoryEntry>>()
    
    /**
     * Record a concept occurrence with timestamp
     */
    fun recordOccurrence(concept: ConceptKernel.Concept, severity: Double, context: String = "") {
        val entry = MemoryEntry(
            conceptId = concept.id,
            severity = severity,
            timestamp = getTimeMillis(),
            context = context
        )
        
        if (!memoryStore.containsKey(concept.id)) {
            memoryStore[concept.id] = mutableListOf()
        }
        memoryStore[concept.id]?.add(entry)
        
        Log.d(TAG, "Recorded occurrence: ${concept.name} with severity $severity")
        
        // Prune old entries periodically
        if (memoryStore[concept.id]?.size ?: 0 > escalationWindow * 2) {
            pruneOldEntries(concept.id)
        }
    }
    
    /**
     * Compute exponential decay memory for a concept
     * Memory(c) = Σₜ cₜ · e^(−λt)
     */
    fun computeMemory(conceptId: String): Double {
        val entries = memoryStore[conceptId] ?: return 0.0
        
        val currentTime = getTimeMillis()
        var memorySum = 0.0
        
        entries.forEach { entry ->
            val timeDiff = (currentTime - entry.timestamp) / 1000.0 // Convert to seconds
            val decayFactor = exp(-decayLambda * timeDiff)
            memorySum += entry.severity * decayFactor
        }
        
        Log.d(TAG, "Memory for $conceptId: $memorySum")
        return memorySum
    }
    
    /**
     * Detect repetition pattern
     * Repetition = Occurrences / TimeWindow
     */
    fun detectRepetition(conceptId: String, timeWindowSeconds: Long = 3600): Double {
        val entries = memoryStore[conceptId] ?: return 0.0
        
        val currentTime = getTimeMillis()
        val windowStart = currentTime - (timeWindowSeconds * 1000)
        
        val recentOccurrences = entries.count { it.timestamp >= windowStart }
        val repetition = recentOccurrences.toDouble() / timeWindowSeconds
        
        Log.d(TAG, "Repetition for $conceptId: $repetition ($recentOccurrences occurrences)")
        return repetition
    }
    
    /**
     * Detect escalation pattern
     * Escalation = Severityₜ − Severityₜ₋₁
     */
    fun detectEscalation(conceptId: String): Double {
        val entries = memoryStore[conceptId] ?: return 0.0
        
        if (entries.size < 2) {
            return 0.0
        }
        
        // Sort by timestamp
        val sortedEntries = entries.sortedBy { it.timestamp }
        
        // Get last 'escalationWindow' entries
        val recentEntries = sortedEntries.takeLast(escalationWindow)
        
        if (recentEntries.size < 2) {
            return 0.0
        }
        
        // Compute average severity of first half vs second half
        val midPoint = recentEntries.size / 2
        val firstHalfAvg = recentEntries.subList(0, midPoint).averageOf { it.severity }
        val secondHalfAvg = recentEntries.subList(midPoint, recentEntries.size).averageOf { it.severity }
        
        val escalation = secondHalfAvg - firstHalfAvg
        
        Log.d(TAG, "Escalation for $conceptId: $escalation (from $firstHalfAvg to $secondHalfAvg)")
        return escalation
    }
    
    /**
     * Get temporal pattern analysis for a concept
     */
    fun analyzeTemporalPattern(conceptId: String): TemporalPattern {
        val memory = computeMemory(conceptId)
        val repetition = detectRepetition(conceptId)
        val escalation = detectEscalation(conceptId)
        
        val pattern = when {
            escalation > 0.3 && repetition > 0.5 -> TemporalPattern.ESCALATING_FREQUENT
            escalation > 0.3 -> TemporalPattern.ESCALATING
            repetition > 0.5 -> TemporalPattern.FREQUENT
            memory > 0.7 -> TemporalPattern.PERSISTENT
            else -> TemporalPattern.NORMAL
        }
        
        Log.i(TAG, "Temporal pattern for $conceptId: $pattern")
        
        return TemporalPattern(
            pattern = pattern,
            memory = memory,
            repetition = repetition,
            escalation = escalation
        )
    }
    
    /**
     * Prune old entries to prevent memory bloat
     */
    private fun pruneOldEntries(conceptId: String) {
        val entries = memoryStore[conceptId] ?: return
        
        val currentTime = getTimeMillis()
        val maxAgeSeconds = escalationWindow * 3600 // Keep entries for N hours
        
        val prunedEntries = entries.filter { entry ->
            val ageSeconds = (currentTime - entry.timestamp) / 1000.0
            ageSeconds < maxAgeSeconds
        }.toMutableList()
        
        memoryStore[conceptId] = prunedEntries
        
        Log.d(TAG, "Pruned old entries for $conceptId: ${entries.size - prunedEntries.size} removed")
    }
    
    /**
     * Clear memory for a specific concept
     */
    fun clearConceptMemory(conceptId: String) {
        memoryStore.remove(conceptId)
        Log.d(TAG, "Cleared memory for $conceptId")
    }
    
    /**
     * Clear all memory
     */
    fun clearAllMemory() {
        memoryStore.clear()
        Log.d(TAG, "Cleared all memory")
    }
    
    /**
     * Get all tracked concept IDs
     */
    fun getTrackedConcepts(): Set<String> {
        return memoryStore.keys.toSet()
    }
    
    /**
     * Temporal pattern types
     */
    enum class TemporalPattern {
        NORMAL,           // No concerning patterns
        FREQUENT,         // High repetition rate
        ESCALATING,       // Increasing severity
        ESCALATING_FREQUENT, // Both escalating and frequent
        PERSISTENT        // Long-term memory presence
    }
    
    /**
     * Temporal pattern analysis result
     */
    data class TemporalPattern(
        val pattern: TemporalPattern,
        val memory: Double,
        val repetition: Double,
        val escalation: Double
    )
}
