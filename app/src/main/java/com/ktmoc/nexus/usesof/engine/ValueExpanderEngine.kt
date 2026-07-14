package com.ktmoc.nexus.usesof.engine

import android.util.Log
import com.ktmoc.nexus.usesof.kernel.ConceptKernel
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Value Expander Engine for USESOF Framework
 * Recursively expands concepts into full semantic lattices
 * 
 * WATERMARKED FOR KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU
 * License: kingtravismo@gmail.com
 */
class ValueExpanderEngine(
    private val conceptKernel: ConceptKernel,
    private val maxDepth: Int = 8,
    private val branchFactor: Int = 4,
    private val mutationRate: Double = 0.5,
    private val mutationRandomness: Double = 0.25
) {
    
    companion object {
        private const val TAG = "ValueExpander"
    }
    
    /**
     * Fully expand a concept with all semantic relations
     * E(C) = { S(C), O(C), R(C), V(C), D(C), A(C) }
     */
    suspend fun expandConcept(
        seedConcept: ConceptKernel.Concept,
        currentDepth: Int = 0
    ): ConceptKernel.Concept {
        if (currentDepth >= maxDepth || !seedConcept.expandable) {
            return seedConcept
        }
        
        Log.d(TAG, "Expanding concept: ${seedConcept.name} at depth $currentDepth")
        
        // Generate synonyms
        val synonyms = if (seedConcept.expansionRules["generateSynonyms"] == true) {
            generateSynonyms(seedConcept, branchFactor)
        } else {
            seedConcept.synonyms
        }
        
        // Generate opposites
        val opposites = if (seedConcept.expansionRules["generateOpposites"] == true) {
            generateOpposites(seedConcept, branchFactor)
        } else {
            seedConcept.opposites
        }
        
        // Generate related concepts
        val relatedConcepts = if (seedConcept.expansionRules["generateRelatedConcepts"] == true) {
            generateRelatedConcepts(seedConcept, branchFactor)
        } else {
            seedConcept.relatedConcepts
        }
        
        // Generate intensity spectrum
        val intensityVector = if (seedConcept.expansionRules["generateIntensitySpectrum"] == true) {
            generateIntensitySpectrum(seedConcept)
        } else {
            seedConcept.intensityVector
        }
        
        // Generate dimensional scores
        val dimensions = if (seedConcept.expansionRules["generateDimensions"] == true) {
            generateDimensions(seedConcept)
        } else {
            seedConcept.dimensions
        }
        
        // Generate semantic axes
        val semanticAxes = if (seedConcept.expansionRules["generateSemanticAxes"] == true) {
            generateSemanticAxes(seedConcept)
        } else {
            seedConcept.semanticAxes
        }
        
        // Apply mutation to explore nearby semantic spaces
        val mutatedIntensity = applyMutation(intensityVector)
        val mutatedDimensions = applyMutation(dimensions)
        val mutatedAxes = applyMutation(semanticAxes)
        
        // Build expanded concept
        return seedConcept.copy(
            synonyms = synonyms,
            opposites = opposites,
            relatedConcepts = relatedConcepts,
            intensityVector = mutatedIntensity,
            dimensions = mutatedDimensions,
            semanticAxes = mutatedAxes,
            expansionRules = seedConcept.expansionRules + mapOf("expanded" to true)
        )
    }
    
    /**
     * Generate synonyms using semantic transforms
     */
    private fun generateSynonyms(concept: ConceptKernel.Concept, count: Int): List<String> {
        val baseSynonyms = concept.synonyms.toMutableList()
        
        // Add variations with prefixes/suffixes
        val prefixes = listOf("ultra-", "hyper-", "extreme-", "mega-")
        val suffixes = listOf("-ish", "-like", "-esque")
        
        while (baseSynonyms.size < count && baseSynonyms.isNotEmpty()) {
            val base = baseSynonyms.random()
            val variation = when (Random.nextInt(3)) {
                0 -> prefixes.random() + base
                1 -> base + suffixes.random()
                2 -> base.replaceFirstChar { it.uppercase() }
                else -> base
            }
            if (variation !in baseSynonyms) {
                baseSynonyms.add(variation)
            }
        }
        
        return baseSynonyms.take(count)
    }
    
    /**
     * Generate opposites using semantic inversion
     */
    private fun generateOpposites(concept: ConceptKernel.Concept, count: Int): List<String> {
        val baseOpposites = concept.opposites.toMutableList()
        
        // Add antonym variations
        val antonymPrefixes = listOf("un-", "non-", "anti-", "de-")
        
        while (baseOpposites.size < count && baseOpposites.isNotEmpty()) {
            val base = baseOpposites.random()
            val variation = when (Random.nextInt(3)) {
                0 -> antonymPrefixes.random() + base
                1 -> base.replaceFirstChar { it.lowercase() }
                2 -> "less_$base"
                else -> base
            }
            if (variation !in baseOpposites) {
                baseOpposites.add(variation)
            }
        }
        
        return baseOpposites.take(count)
    }
    
    /**
     * Generate related concepts through semantic association
     */
    private fun generateRelatedConcepts(concept: ConceptKernel.Concept, count: Int): List<String> {
        val baseRelated = concept.relatedConcepts.toMutableList()
        
        // Add contextual associations
        val contexts = listOf("digital_", "social_", "psychological_", "systemic_")
        
        while (baseRelated.size < count) {
            val base = (concept.synonyms + concept.opposites + concept.relatedConcepts).randomOrNull() 
                ?: concept.name
            val variation = when (Random.nextInt(4)) {
                0 -> contexts.random() + base
                1 -> "${base}_pattern"
                2 -> "${base}_behavior"
                3 -> "${base}_indicator"
                else -> base
            }
            if (variation !in baseRelated) {
                baseRelated.add(variation)
            }
        }
        
        return baseRelated.take(count)
    }
    
    /**
     * Generate intensity vector across multiple dimensions
     */
    private fun generateIntensitySpectrum(concept: ConceptKernel.Concept): Map<String, Double> {
        val baseIntensity = concept.intensityVector
        
        // Define standard intensity dimensions
        val dimensions = listOf(
            "hostility", "aggression", "malice", "cruelty",
            "humiliation", "dominance", "exclusion"
        )
        
        return dimensions.associateWith { dim ->
            baseIntensity[dim] ?: Random.nextDouble(0.0, 1.0)
        }.mapValues { (_, value) ->
            // Normalize to [0, 1]
            value.coerceIn(0.0, 1.0)
        }
    }
    
    /**
     * Generate dimensional scores
     */
    private fun generateDimensions(concept: ConceptKernel.Concept): Map<String, Double> {
        val baseDimensions = concept.dimensions
        
        // Define standard dimensions
        val dimensions = listOf(
            "intent", "severity", "frequency", "certainty", "impact"
        )
        
        return dimensions.associateWith { dim ->
            baseDimensions[dim] ?: Random.nextDouble(0.0, 1.0)
        }.mapValues { (_, value) ->
            value.coerceIn(0.0, 1.0)
        }
    }
    
    /**
     * Generate semantic axes in [-1, 1] range
     */
    private fun generateSemanticAxes(concept: ConceptKernel.Concept): Map<String, Double> {
        val baseAxes = concept.semanticAxes
        
        // Define standard semantic axes
        val axes = listOf(
            "humanity", "empathy", "respect", "violence",
            "social_harm", "psychological_harm"
        )
        
        return axes.associateWith { axis ->
            baseAxes[axis] ?: Random.nextDouble(-1.0, 1.0)
        }.mapValues { (_, value) ->
            value.coerceIn(-1.0, 1.0)
        }
    }
    
    /**
     * Apply mutation to explore nearby semantic spaces
     * 25% noise, 50% rate as per specification
     */
    private fun applyMutation(original: Map<String, Double>): Map<String, Double> {
        return original.mapValues { (key, value) ->
            if (Random.nextDouble() < mutationRate) {
                // Apply random mutation
                val noise = Random.nextDouble(-mutationRandomness, mutationRandomness)
                (value + noise).coerceIn(
                    if (value >= 0) 0.0 else -1.0,
                    if (value >= 0) 1.0 else 0.0
                )
            } else {
                value
            }
        }
    }
    
    /**
     * Recursive expansion with depth tracking
     */
    suspend fun expandRecursive(
        seedConcept: ConceptKernel.Concept,
        visitedConcepts: MutableSet<String> = mutableSetOf()
    ): List<ConceptKernel.Concept> {
        val expanded = mutableListOf<ConceptKernel.Concept>()
        
        if (seedConcept.id in visitedConcepts) {
            return expanded
        }
        
        visitedConcepts.add(seedConcept.id)
        
        // Expand current concept
        val currentExpanded = expandConcept(seedConcept)
        expanded.add(currentExpanded)
        
        // Recursively expand related concepts up to max depth
        val currentDepth = visitedConcepts.size
        if (currentDepth < maxDepth) {
            currentExpanded.relatedConcepts.forEach { relatedName ->
                val relatedConcept = seedConcept.copy(
                    id = "concept.$relatedName",
                    name = relatedName,
                    synonyms = emptyList(),
                    opposites = emptyList(),
                    relatedConcepts = emptyList()
                )
                expanded.addAll(expandRecursive(relatedConcept, visitedConcepts))
            }
        }
        
        return expanded
    }
    
    /**
     * Compute cosine similarity between two concept vectors
     * sim(A, B) = (A · B) / (||A|| · ||B||)
     */
    fun cosineSimilarity(vecA: Map<String, Double>, vecB: Map<String, Double>): Double {
        val commonKeys = vecA.keys intersect vecB.keys
        if (commonKeys.isEmpty()) return 0.0
        
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        
        commonKeys.forEach { key ->
            val a = vecA[key] ?: 0.0
            val b = vecB[key] ?: 0.0
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }
        
        if (normA == 0.0 || normB == 0.0) return 0.0
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB))
    }
}
