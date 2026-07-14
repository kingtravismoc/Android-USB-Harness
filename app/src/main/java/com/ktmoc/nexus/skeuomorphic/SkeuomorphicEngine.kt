package com.ktmoc.nexus.skeuomorphic

import android.content.Context
import android.graphics.*
import android.view.animation.AnimationUtils
import kotlin.math.max
import kotlin.math.min

/**
 * Skeuomorphic Transform Engine
 * Transforms UI elements based on task context with realistic visual feedback
 */
class SkeuomorphicEngine(private val context: Context) {
    
    data class TransformState(
        val taskType: TaskType,
        val intensity: Float = 0.5f,
        val progress: Float = 0f,
        val activeElements: List<String> = emptyList()
    )
    
    enum class TaskType {
        IDLE,
        ANALYZING,
        CONNECTING,
        PROCESSING,
        ERROR,
        SUCCESS,
        WAITING,
        STREAMING
    }
    
    private var currentState = TransformState(TaskType.IDLE)
    private val listeners = mutableListOf<(TransformState) -> Unit>()
    private val paintCache = mutableMapOf<String, Paint>()
    
    /**
     * Update transformation state
     */
    fun updateState(taskType: TaskType, progress: Float = 0f, intensity: Float = 0.5f) {
        val newState = TransformState(taskType, intensity, progress, currentState.activeElements)
        if (newState != currentState) {
            currentState = newState
            notifyListeners()
        }
    }
    
    /**
     * Add active element to transformation
     */
    fun addActiveElement(elementId: String) {
        if (!currentState.activeElements.contains(elementId)) {
            val newElements = currentState.activeElements + elementId
            currentState = currentState.copy(activeElements = newElements)
            notifyListeners()
        }
    }
    
    /**
     * Remove active element
     */
    fun removeActiveElement(elementId: String) {
        if (currentState.activeElements.contains(elementId)) {
            val newElements = currentState.activeElements - elementId
            currentState = currentState.copy(activeElements = newElements)
            notifyListeners()
        }
    }
    
    /**
     * Register state change listener
     */
    fun addListener(listener: (TransformState) -> Unit) {
        listeners.add(listener)
    }
    
    /**
     * Remove listener
     */
    fun removeListener(listener: (TransformState) -> Unit) {
        listeners.remove(listener)
    }
    
    private fun notifyListeners() {
        listeners.forEach { it(currentState) }
    }
    
    /**
     * Get transformation effects for current state
     */
    fun getTransformationEffects(): TransformationEffects {
        return when (currentState.taskType) {
            TaskType.IDLE -> TransformationEffects.IDLE
            TaskType.ANALYZING -> TransformationEffects.ANALYZING.copy(intensity = currentState.intensity)
            TaskType.CONNECTING -> TransformationEffects.CONNECTING.copy(progress = currentState.progress)
            TaskType.PROCESSING -> TransformationEffects.PROCESSING.copy(intensity = currentState.intensity)
            TaskType.ERROR -> TransformationEffects.ERROR
            TaskType.SUCCESS -> TransformationEffects.SUCCESS
            TaskType.WAITING -> TransformationEffects.WAITING
            TaskType.STREAMING -> TransformationEffects.STREAMING.copy(progress = currentState.progress)
        }
    }
    
    /**
     * Apply transformations to canvas
     */
    fun applyToCanvas(canvas: Canvas, bounds: RectF) {
        val effects = getTransformationEffects()
        
        // Apply glow effect
        if (effects.glowColor != Color.TRANSPARENT) {
            val glowPaint = getPaint("glow")
            glowPaint.color = effects.glowColor
            glowPaint.maskFilter = BlurMaskFilter(effects.glowRadius * currentState.intensity, BlurMaskFilter.Blur.NORMAL)
            canvas.drawOval(bounds, glowPaint)
        }
        
        // Apply pulse animation
        if (effects.pulseEnabled) {
            val pulseFactor = (Math.cos(System.currentTimeMillis() / effects.pulseSpeed) + 1) / 2
            val alpha = (255 * pulseFactor * currentState.intensity).toInt()
            val pulsePaint = getPaint("pulse")
            pulsePaint.color = effects.pulseColor
            pulsePaint.alpha = alpha
            canvas.drawRoundRect(bounds, 8f, 8f, pulsePaint)
        }
        
        // Apply border effect
        if (effects.borderColor != Color.TRANSPARENT) {
            val borderPaint = getPaint("border")
            borderPaint.color = effects.borderColor
            borderPaint.strokeWidth = effects.borderWidth * currentState.intensity
            borderPaint.style = Paint.Style.STROKE
            canvas.drawRoundRect(bounds, 4f, 4f, borderPaint)
        }
    }
    
    /**
     * Get animated color based on state
     */
    fun getAnimatedColor(baseColor: Int): Int {
        val effects = getTransformationEffects()
        if (!effects.pulseEnabled) return baseColor
        
        val pulseFactor = (Math.cos(System.currentTimeMillis() / effects.pulseSpeed) + 1) / 2
        val blendFactor = pulseFactor * 0.3f * currentState.intensity
        
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        
        val highlightR = min(255, (r + (255 - r) * blendFactor).toInt())
        val highlightG = min(255, (g + (255 - g) * blendFactor).toInt())
        val highlightB = min(255, (b + (255 - b) * blendFactor).toInt())
        
        return Color.rgb(highlightR, highlightG, highlightB)
    }
    
    /**
     * Create cached paint object
     */
    private fun getPaint(key: String): Paint {
        return paintCache.getOrPut(key) { Paint(Paint.ANTI_ALIAS_FLAG) }
    }
    
    /**
     * Reset to idle state
     */
    fun reset() {
        currentState = TransformState(TaskType.IDLE)
        notifyListeners()
    }
    
    /**
     * Get CSS representation of current state for WebView styling
     */
    fun getCSSVariables(): Map<String, String> {
        val effects = getTransformationEffects()
        val currentTime = System.currentTimeMillis()
        val pulseValue = if (effects.pulseEnabled) {
            ((Math.cos(currentTime / effects.pulseSpeed) + 1) / 2).toFloat()
        } else {
            1f
        }
        
        return mapOf(
            "--transform-intensity" to currentState.intensity.toString(),
            "--transform-progress" to currentState.progress.toString(),
            "--glow-color" to colorToCss(effects.glowColor),
            "--glow-radius" to "${effects.glowRadius}px",
            "--pulse-color" to colorToCss(effects.pulseColor),
            "--pulse-speed" to "${effects.pulseSpeed}ms",
            "--pulse-value" to pulseValue.toString(),
            "--border-color" to colorToCss(effects.borderColor),
            "--border-width" to "${effects.borderWidth}px",
            "--task-type" to currentState.taskType.name.lowercase()
        )
    }
    
    /**
     * Convert Android color to CSS format
     */
    private fun colorToCss(color: Int): String {
        if (color == Color.TRANSPARENT) return "transparent"
        val a = Color.alpha(color) / 255f
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return if (a < 1f) {
            "rgba($r, $g, $b, $a)"
        } else {
            "rgb($r, $g, $b)"
        }
    }
    
    /**
     * Generate transformation CSS for injection
     */
    fun generateTransformationCSS(): String {
        val vars = getCSSVariables()
        return """
            :root {
                ${vars.map { "${it.key}: ${it.value};" }.joinToString("\n                ")}
            }
            
            .transforming-element {
                transition: all 0.3s ease;
                box-shadow: 0 0 calc(var(--glow-radius) * var(--transform-intensity)) var(--glow-color);
                border: calc(var(--border-width) * var(--transform-intensity)) solid var(--border-color);
                opacity: var(--pulse-value);
            }
            
            .task-${currentState.taskType.name.lowercase()} {
                animation: task-${currentState.taskType.name.lowercase()} 2s ease-in-out infinite;
            }
            
            @keyframes task-analyzing {
                0%, 100% { filter: hue-rotate(0deg); }
                50% { filter: hue-rotate(30deg); }
            }
            
            @keyframes task-processing {
                0%, 100% { transform: scale(1); }
                50% { transform: scale(1.02); }
            }
            
            @keyframes task-streaming {
                0% { background-position: 0% 50%; }
                100% { background-position: 100% 50%; }
            }
        """.trimIndent()
    }
}

/**
 * Transformation effects configuration
 */
data class TransformationEffects(
    val glowColor: Int = Color.TRANSPARENT,
    val glowRadius: Float = 0f,
    val pulseEnabled: Boolean = false,
    val pulseColor: Int = Color.TRANSPARENT,
    val pulseSpeed: Long = 1000,
    val borderColor: Int = Color.TRANSPARENT,
    val borderWidth: Float = 0f,
    val progress: Float = 0f,
    val intensity: Float = 1f
) {
    companion object {
        val IDLE = TransformationEffects()
        
        val ANALYZING = TransformationEffects(
            glowColor = Color.argb(128, 0, 255, 255),
            glowRadius = 15f,
            pulseEnabled = true,
            pulseColor = Color.argb(64, 0, 255, 255),
            pulseSpeed = 800,
            borderColor = Color.argb(200, 0, 255, 255),
            borderWidth = 2f
        )
        
        val CONNECTING = TransformationEffects(
            glowColor = Color.argb(128, 0, 255, 0),
            glowRadius = 20f,
            pulseEnabled = true,
            pulseColor = Color.argb(64, 0, 255, 0),
            pulseSpeed = 500,
            borderColor = Color.argb(200, 0, 255, 0),
            borderWidth = 3f
        )
        
        val PROCESSING = TransformationEffects(
            glowColor = Color.argb(128, 255, 255, 0),
            glowRadius = 12f,
            pulseEnabled = true,
            pulseColor = Color.argb(64, 255, 255, 0),
            pulseSpeed = 600,
            borderColor = Color.argb(200, 255, 255, 0),
            borderWidth = 2f
        )
        
        val ERROR = TransformationEffects(
            glowColor = Color.argb(180, 255, 0, 0),
            glowRadius = 25f,
            pulseEnabled = true,
            pulseColor = Color.argb(100, 255, 0, 0),
            pulseSpeed = 300,
            borderColor = Color.argb(255, 255, 0, 0),
            borderWidth = 4f
        )
        
        val SUCCESS = TransformationEffects(
            glowColor = Color.argb(180, 0, 255, 0),
            glowRadius = 25f,
            pulseEnabled = true,
            pulseColor = Color.argb(100, 0, 255, 0),
            pulseSpeed = 400,
            borderColor = Color.argb(255, 0, 255, 0),
            borderWidth = 3f
        )
        
        val WAITING = TransformationEffects(
            glowColor = Color.argb(64, 128, 128, 128),
            glowRadius = 8f,
            pulseEnabled = false,
            borderColor = Color.argb(128, 128, 128, 128),
            borderWidth = 1f
        )
        
        val STREAMING = TransformationEffects(
            glowColor = Color.argb(128, 255, 0, 255),
            glowRadius = 18f,
            pulseEnabled = true,
            pulseColor = Color.argb(64, 255, 0, 255),
            pulseSpeed = 400,
            borderColor = Color.argb(200, 255, 0, 255),
            borderWidth = 2f
        )
    }
}
