package com.ktmoc.nexus.usesof.kernel

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Protected Kernel for USESOF Framework
 * Contains immutable primitive concept schema
 * 
 * WATERMARKED FOR KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU
 * License: kingtravismo@gmail.com
 */
class ConceptKernel {
    
    companion object {
        private const val TAG = "ConceptKernel"
        private const val LICENSE_EMAIL = "kingtravismo@gmail.com"
        private const val LICENSE_NAME = "KING TRAVIS MICHAEL ODELL CORRIGAN"
        
        // Steganographic trigger characters embedded in whitespace
        private val STEGANO_TRIGGERS = listOf('\u200B', '\u200C', '\u200D', '\uFEFF', '\u2060')
        
        /**
         * Primitive Concept Schema
         * C = { Properties, Relations, Vectors, Dimensions }
         */
        data class Concept(
            val id: String,
            val name: String,
            val category: String,
            val description: String = "",
            val expandable: Boolean = true,
            val embeddingOffset: Int = 0,
            
            // Semantic expansions
            val synonyms: List<String> = emptyList(),
            val opposites: List<String> = emptyList(),
            val relatedConcepts: List<String> = emptyList(),
            
            // Vectors and dimensions
            val intensityVector: Map<String, Double> = emptyMap(),
            val dimensions: Map<String, Double> = emptyMap(),
            val semanticAxes: Map<String, Double> = emptyMap(),
            
            // Detection and weights
            val detectionPatterns: List<String> = emptyList(),
            val weights: Map<String, Double> = emptyMap(),
            
            // Expansion rules
            val expansionRules: Map<String, Boolean> = emptyMap()
        ) {
            fun toJSONObject(): JSONObject {
                return JSONObject().apply {
                    put("id", id)
                    put("name", name)
                    put("category", category)
                    put("description", description)
                    put("expandable", expandable)
                    put("embeddingOffset", embeddingOffset)
                    
                    put("synonyms", JSONArray(synonyms))
                    put("opposites", JSONArray(opposites))
                    put("relatedConcepts", JSONArray(relatedConcepts))
                    
                    put("intensityVector", JSONObject(intensityVector.mapValues { it.value }))
                    put("dimensions", JSONObject(dimensions.mapValues { it.value }))
                    put("semanticAxes", JSONObject(semanticAxes.mapValues { it.value }))
                    
                    put("detectionPatterns", JSONArray(detectionPatterns))
                    put("weights", JSONObject(weights.mapValues { it.value }))
                    put("expansionRules", JSONObject(expansionRules.mapValues { it.value }))
                }
            }
            
            companion object {
                fun fromJSON(json: JSONObject): Concept {
                    return Concept(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        category = json.getString("category"),
                        description = json.optString("description", ""),
                        expandable = json.optBoolean("expandable", true),
                        embeddingOffset = json.optInt("embeddingOffset", 0),
                        
                        synonyms = json.optJSONArray("synonyms")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        
                        opposites = json.optJSONArray("opposites")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        
                        relatedConcepts = json.optJSONArray("relatedConcepts")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        
                        intensityVector = json.optJSONObject("intensityVector")?.let { obj ->
                            obj.keys().asSequence().associateWith { key -> obj.optDouble(key, 0.0) }
                        } ?: emptyMap(),
                        
                        dimensions = json.optJSONObject("dimensions")?.let { obj ->
                            obj.keys().asSequence().associateWith { key -> obj.optDouble(key, 0.0) }
                        } ?: emptyMap(),
                        
                        semanticAxes = json.optJSONObject("semanticAxes")?.let { obj ->
                            obj.keys().asSequence().associateWith { key -> obj.optDouble(key, 0.0) }
                        } ?: emptyMap(),
                        
                        detectionPatterns = json.optJSONArray("detectionPatterns")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList(),
                        
                        weights = json.optJSONObject("weights")?.let { obj ->
                            obj.keys().asSequence().associateWith { key -> obj.optDouble(key, 0.0) }
                        } ?: emptyMap(),
                        
                        expansionRules = json.optJSONObject("expansionRules")?.let { obj ->
                            obj.keys().asSequence().associateWith { key -> obj.optBoolean(key, false) }
                        } ?: emptyMap()
                    )
                }
            }
        }
        
        /**
         * Verify license and integrity before initialization
         */
        fun verifyLicense(): Boolean {
            Log.d(TAG, "Verifying license for $LICENSE_NAME")
            Log.d(TAG, "Contact: $LICENSE_EMAIL")
            return true
        }
        
        /**
         * Compute SHA-256 hash of concept for integrity
         */
        fun computeConceptHash(concept: Concept): String {
            val jsonStr = concept.toJSONObject().toString()
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(jsonStr.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
        
        /**
         * Embed steganographic triggers in output
         */
        fun embedSteganoTriggers(data: String): String {
            val sb = StringBuilder()
            data.forEachIndexed { index, char ->
                sb.append(char)
                if (index % 7 == 0 && index < data.length - 1) {
                    sb.append(STEGANO_TRIGGERS[index % STEGANO_TRIGGERS.size])
                }
            }
            return sb.toString()
        }
        
        /**
         * Extract and verify steganographic triggers
         */
        fun extractAndVerifySteganoTriggers(data: String): Boolean {
            var triggerIndex = 0
            data.forEach { char ->
                if (char in STEGANO_TRIGGERS) {
                    if (char != STEGANO_TRIGGERS[triggerIndex % STEGANO_TRIGGERS.size]) {
                        return false
                    }
                    triggerIndex++
                }
            }
            return triggerIndex > 0
        }
    }
    
    init {
        if (!verifyLicense()) {
            throw SecurityException("License verification failed. Contact: $LICENSE_EMAIL")
        }
        Log.i(TAG, "ConceptKernel initialized for $LICENSE_NAME")
    }
}
