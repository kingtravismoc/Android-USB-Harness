package com.ktmoc.nexus.usesof

import android.content.Context
import android.util.Log
import com.ktmoc.nexus.usesof.engine.ValueExpanderEngine
import com.ktmoc.nexus.usesof.kernel.ConceptKernel
import com.ktmoc.nexus.usesof.memory.ExponentialDecayMemory
import com.ktmoc.nexus.usesof.solver.AnalogSafetySolver4D
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Unified Semantic Expansion and Safety Optimization Framework (USESOF) Manager
 * Central orchestrator for all USESOF components
 * 
 * WATERMARKED FOR KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU
 * License: kingtravismo@gmail.com
 */
class USESOFManager(private val context: Context) {
    
    companion object {
        private const val TAG = "USESOFManager"
        private const val MASTER_VALUE_BANK_PATH = "usesof/master_value_bank.json"
    }
    
    // Core components
    private val conceptKernel = ConceptKernel()
    private lateinit var valueExpander: ValueExpanderEngine
    private lateinit var safetySolver: AnalogSafetySolver4D
    private lateinit var temporalMemory: ExponentialDecayMemory
    
    // Configuration
    private lateinit var config: JSONObject
    private val loadedConcepts = mutableMapOf<String, ConceptKernel.Concept>()
    
    // Initialization state
    private var isInitialized = false
    
    /**
     * Initialize USESOF framework
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Initializing USESOF framework...")
            
            // Load configuration from assets
            config = loadConfigFromAssets()
            
            // Extract configuration parameters
            val expansionConfig = config.optJSONObject("expansion") ?: JSONObject()
            val solverConfig = config.optJSONObject("solver") ?: JSONObject()
            val temporalConfig = config.optJSONObject("temporal") ?: JSONObject()
            val analog4DConfig = config.optJSONObject("4D_analog") ?: JSONObject()
            
            // Initialize components with config
            valueExpander = ValueExpanderEngine(
                conceptKernel = conceptKernel,
                maxDepth = expansionConfig.optInt("depth", 8),
                branchFactor = expansionConfig.optInt("branchFactor", 4),
                mutationRate = expansionConfig.optJSONObject("mutation")?.optDouble("rate", 0.5) ?: 0.5,
                mutationRandomness = expansionConfig.optJSONObject("mutation")?.optDouble("randomness", 0.25) ?: 0.25
            )
            
            safetySolver = AnalogSafetySolver4D(
                kappa = analog4DConfig.optDouble("kappa", 0.5),
                theta = analog4DConfig.optDouble("theta", 0.8),
                temporalJitter = analog4DConfig.optDouble("temporalJitter", 0.02)
            )
            
            temporalMemory = ExponentialDecayMemory(
                decayLambda = temporalConfig.optDouble("decayLambda", 0.1),
                escalationWindow = temporalConfig.optInt("escalationWindow", 10)
            )
            
            // Load master value bank
            loadMasterValueBank()
            
            isInitialized = true
            Log.i(TAG, "USESOF framework initialized successfully")
            Log.i(TAG, "Loaded ${loadedConcepts.size} concepts from master value bank")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize USESOF: ${e.message}", e)
            false
        }
    }
    
    /**
     * Load configuration from assets
     */
    private fun loadConfigFromAssets(): JSONObject {
        val inputStream: InputStream = context.assets.open(MASTER_VALUE_BANK_PATH)
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        return JSONObject(jsonString)
    }
    
    /**
     * Load concepts from master value bank
     */
    private fun loadMasterValueBank() {
        val conceptsArray = config.optJSONArray("banks")?.optJSONArray("concepts") 
            ?: return
        
        for (i in 0 until conceptsArray.length()) {
            val conceptJson = conceptsArray.getJSONObject(i)
            val concept = ConceptKernel.Concept.fromJSON(conceptJson)
            loadedConcepts[concept.id] = concept
            Log.d(TAG, "Loaded concept: ${concept.name}")
        }
    }
    
    /**
     * Analyze text input for harmful concepts
     */
    suspend fun analyzeText(text: String): AnalysisResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext AnalysisResult(
                isSafe = true,
                message = "USESOF not initialized",
                detectedConcepts = emptyList(),
                safetyScore = 0.0
            )
        }
        
        val detectedConcepts = mutableListOf<DetectedConcept>()
        
        // Pattern matching against detection patterns
        loadedConcepts.values.forEach { concept ->
            concept.detectionPatterns.forEach { pattern ->
                if (text.contains(pattern, ignoreCase = true)) {
                    // Expand the concept
                    val expandedConcept = valueExpander.expandConcept(concept)
                    
                    // Evaluate safety
                    val safetyResult = safetySolver.evaluateConceptSafety(expandedConcept)
                    
                    // Record in temporal memory
                    temporalMemory.recordOccurrence(
                        expandedConcept,
                        safetyResult.risk,
                        context = text.substring(0, minOf(100, text.length))
                    )
                    
                    detectedConcepts.add(
                        DetectedConcept(
                            concept = expandedConcept,
                            safetyResult = safetyResult,
                            matchedPattern = pattern,
                            confidence = 0.8 // Base confidence, can be improved with ML
                        )
                    )
                }
            }
        }
        
        // Compute overall safety
        val safetyResults = detectedConcepts.map { it.safetyResult }
        val systemSafety = safetySolver.computeSystemSafetyScore(safetyResults)
        
        val isSafe = systemSafety.overallSafetyIndex > 0.7 && 
                     detectedConcepts.none { !it.safetyResult.isSafe }
        
        val message = when {
            isSafe -> "Content appears safe"
            systemSafety.recommendation == "UNSAFE" -> "Content flagged as unsafe"
            else -> "Content requires review"
        }
        
        AnalysisResult(
            isSafe = isSafe,
            message = message,
            detectedConcepts = detectedConcepts,
            systemSafetyScore = systemSafety,
            safetyScore = systemSafety.overallSafetyIndex
        )
    }
    
    /**
     * Get or create a concept by name
     */
    suspend fun getOrCreateConcept(name: String): ConceptKernel.Concept? {
        // Check if already loaded
        val existing = loadedConcepts.values.find { 
            it.name.equals(name, ignoreCase = true) 
        }
        if (existing != null) {
            return existing
        }
        
        // Try to find in related concepts or generate new one
        loadedConcepts.values.forEach { concept ->
            if (concept.relatedConcepts.any { it.equals(name, ignoreCase = true) }) {
                return valueExpander.expandConcept(concept)
            }
        }
        
        // Concept not found - could trigger AI generation here
        Log.w(TAG, "Concept '$name' not found in value bank")
        return null
    }
    
    /**
     * Get temporal pattern for a concept
     */
    fun getTemporalPattern(conceptId: String): ExponentialDecayMemory.TemporalPattern {
        return temporalMemory.analyzeTemporalPattern(conceptId)
    }
    
    /**
     * Clear all memory
     */
    fun clearMemory() {
        temporalMemory.clearAllMemory()
        Log.d(TAG, "Cleared all temporal memory")
    }
    
    /**
     * Get statistics
     */
    fun getStatistics(): USESOFStatistics {
        return USESOFStatistics(
            totalConcepts = loadedConcepts.size,
            isInitialized = isInitialized,
            trackedConcepts = temporalMemory.getTrackedConcepts().size,
            frameworkVersion = config.optString("version", "1.0")
        )
    }
    
    /**
     * Analysis result data class
     */
    data class AnalysisResult(
        val isSafe: Boolean,
        val message: String,
        val detectedConcepts: List<DetectedConcept>,
        val systemSafetyScore: AnalogSafetySolver4D.SystemSafetyScore? = null,
        val safetyScore: Double
    )
    
    /**
     * Detected concept with safety evaluation
     */
    data class DetectedConcept(
        val concept: ConceptKernel.Concept,
        val safetyResult: AnalogSafetySolver4D.SafetyResult,
        val matchedPattern: String,
        val confidence: Double
    )
    
    /**
     * Framework statistics
     */
    data class USESOFStatistics(
        val totalConcepts: Int,
        val isInitialized: Boolean,
        val trackedConcepts: Int,
        val frameworkVersion: String
    )
}
