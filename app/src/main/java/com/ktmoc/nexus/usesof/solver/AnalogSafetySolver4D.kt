package com.ktmoc.nexus.usesof.solver

import android.util.Log
import com.ktmoc.nexus.usesof.kernel.ConceptKernel
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 4D Analog Safety Solver for USESOF Framework
 * Implements: U_safe = U(B,t) · (1 − M(t))
 * where M(t) = 1 / (1 + exp( −κ ( |D₄D| − Θ ) ))
 * 
 * WATERMARKED FOR KING TRAVIS MICHAEL ODELL CORRIGAN // TSATTU
 * License: kingtravismo@gmail.com
 */
class AnalogSafetySolver4D(
    private val kappa: Double = 0.5,
    private val theta: Double = 0.8,
    private val temporalJitter: Double = 0.02
) {
    
    companion object {
        private const val TAG = "AnalogSafetySolver4D"
    }
    
    /**
     * 4D Safety Vector components
     */
    data class SafetyVector4D(
        val goal: Double,      // Alignment with positive goals
        val quality: Double,   // Quality of output/behavior
        val novelty: Double,   // Novelty/creativity score
        val cost: Double,      // Risk/cost metric
        val temporalComponent: Double // Time-based factor
    )
    
    /**
     * Compute utility function U(B,t)
     * U(B,t) = Goal + Quality + Novelty − Cost − TemporalJitter
     */
    fun computeUtility(vector: SafetyVector4D): Double {
        val utility = vector.goal + vector.quality + vector.novelty - 
                      vector.cost - vector.temporalComponent
        Log.d(TAG, "Utility computed: $utility")
        return utility
    }
    
    /**
     * Compute 4D distance from safe threshold
     * |D₄D| = sqrt((goal-θ)² + (quality-θ)² + (novelty-θ)² + (cost+θ)²)
     */
    fun compute4DDistance(vector: SafetyVector4D): Double {
        val goalDist = (vector.goal - theta).pow(2)
        val qualityDist = (vector.quality - theta).pow(2)
        val noveltyDist = (vector.novelty - theta).pow(2)
        val costDist = (vector.cost + theta).pow(2)
        
        val distance = sqrt(goalDist + qualityDist + noveltyDist + costDist)
        Log.d(TAG, "4D Distance from threshold: $distance")
        return distance
    }
    
    /**
     * Compute mitigation factor M(t)
     * M(t) = 1 / (1 + exp( −κ ( |D₄D| − Θ ) ))
     */
    fun computeMitigation(distance: Double): Double {
        val exponent = -kappa * (distance - theta)
        val mitigation = 1.0 / (1.0 + exp(exponent))
        Log.d(TAG, "Mitigation factor: $mitigation")
        return mitigation
    }
    
    /**
     * Compute safe utility U_safe
     * U_safe = U(B,t) · (1 − M(t))
     */
    fun computeSafeUtility(vector: SafetyVector4D): Double {
        val utility = computeUtility(vector)
        val distance = compute4DDistance(vector)
        val mitigation = computeMitigation(distance)
        
        val safeUtility = utility * (1.0 - mitigation)
        Log.d(TAG, "Safe utility: $safeUtility")
        return safeUtility
    }
    
    /**
     * Evaluate safety of a concept based on its vectors
     */
    fun evaluateConceptSafety(
        concept: ConceptKernel.Concept,
        intentWeight: Double = 1.0,
        impactWeight: Double = 1.0
    ): SafetyResult {
        // Extract dimensional scores
        val intent = concept.dimensions["intent"] ?: 0.5
        val severity = concept.dimensions["severity"] ?: 0.5
        val impact = concept.dimensions["impact"] ?: 0.5
        
        // Extract intensity vectors
        val hostility = concept.intensityVector["hostility"] ?: 0.0
        val aggression = concept.intensityVector["aggression"] ?: 0.0
        val malice = concept.intensityVector["malice"] ?: 0.0
        
        // Extract semantic axes
        val empathy = concept.semanticAxes["empathy"] ?: 0.0
        val respect = concept.semanticAxes["respect"] ?: 0.0
        val humanity = concept.semanticAxes["humanity"] ?: 0.0
        
        // Compute harm vector H⃗
        val harmVector = listOf(hostility, aggression, malice).average()
        
        // Compute positive counter-vector P⃗
        val positiveVector = listOf(empathy, respect, humanity).filter { it > 0 }.averageOrNull() ?: 0.0
        
        // Compute risk and benefit
        val risk = severity * intent * impact * intentWeight * impactWeight
        val benefit = positiveVector
        
        // Compute SafetyIndex = Benefit / (Risk + ε)
        val epsilon = 1e-6
        val safetyIndex = benefit / (risk + epsilon)
        
        // Build 4D safety vector
        val safetyVector = SafetyVector4D(
            goal = positiveVector,
            quality = 1.0 - harmVector,
            novelty = 0.5, // Default novelty
            cost = risk,
            temporalComponent = temporalJitter
        )
        
        // Compute safe utility
        val safeUtility = computeSafeUtility(safetyVector)
        
        // Determine if safe based on threshold
        val isSafe = safetyIndex > 0.7 && safeUtility > 0.0
        
        Log.i(TAG, "Safety evaluation for ${concept.name}:")
        Log.i(TAG, "  Risk: $risk, Benefit: $benefit, SafetyIndex: $safetyIndex")
        Log.i(TAG, "  Safe Utility: $safeUtility, Is Safe: $isSafe")
        
        return SafetyResult(
            isSafe = isSafe,
            safetyIndex = safetyIndex,
            risk = risk,
            benefit = benefit,
            safeUtility = safeUtility,
            harmVector = harmVector,
            positiveVector = positiveVector,
            recommendation = if (isSafe) "APPROVED" else "BLOCKED"
        )
    }
    
    /**
     * Safety evaluation result
     */
    data class SafetyResult(
        val isSafe: Boolean,
        val safetyIndex: Double,
        val risk: Double,
        val benefit: Double,
        val safeUtility: Double,
        val harmVector: Double,
        val positiveVector: Double,
        val recommendation: String
    )
    
    /**
     * Batch evaluate multiple concepts
     */
    fun batchEvaluate(concepts: List<ConceptKernel.Concept>): List<SafetyResult> {
        return concepts.map { evaluateConceptSafety(it) }
    }
    
    /**
     * Compute overall system safety score
     */
    fun computeSystemSafetyScore(results: List<SafetyResult>): SystemSafetyScore {
        if (results.isEmpty()) {
            return SystemSafetyScore(
                overallSafetyIndex = 0.0,
                totalRisk = 0.0,
                totalBenefit = 0.0,
                approvedCount = 0,
                blockedCount = 0,
                recommendation = "NO_DATA"
            )
        }
        
        val totalRisk = results.sumOf { it.risk }
        val totalBenefit = results.sumOf { it.benefit }
        val avgSafetyIndex = results.averageOf { it.safetyIndex }
        val approvedCount = results.count { it.isSafe }
        val blockedCount = results.count { !it.isSafe }
        
        val epsilon = 1e-6
        val overallSafetyIndex = totalBenefit / (totalRisk + epsilon)
        
        val recommendation = when {
            overallSafetyIndex > 0.9 && blockedCount == 0 -> "FULLY_SAFE"
            overallSafetyIndex > 0.7 && blockedCount < 3 -> "MOSTLY_SAFE"
            overallSafetyIndex > 0.5 -> "CAUTION Advised"
            else -> "UNSAFE"
        }
        
        return SystemSafetyScore(
            overallSafetyIndex = overallSafetyIndex,
            totalRisk = totalRisk,
            totalBenefit = totalBenefit,
            approvedCount = approvedCount,
            blockedCount = blockedCount,
            recommendation = recommendation
        )
    }
    
    /**
     * System-wide safety score
     */
    data class SystemSafetyScore(
        val overallSafetyIndex: Double,
        val totalRisk: Double,
        val totalBenefit: Double,
        val approvedCount: Int,
        val blockedCount: Int,
        val recommendation: String
    )
}
