package com.ktmoc.nexus.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Hugging Face AI Integration - Free, no-key required for public models
 * Supports shim generation, documentation gathering, and service integration
 */
class HuggingFaceService(private val context: Context) {
    
    companion object {
        private const val TAG = "HuggingFaceService"
        private const val BASE_URL = "https://api-inference.huggingface.co/models"
        
        // Free public models that don't require API keys
        const val MODEL_CODE_GEN = "Salesforce/codegen-350M-mono"
        const val MODEL_TEXT_GEN = "mistralai/Mistral-7B-Instruct-v0.1"
        const val MODEL_DOC_SUMMARY = "facebook/bart-large-cnn"
        const val MODEL_CODE_EXPLAIN = "bigcode/starcoderbase-3b"
    }
    
    private val preferences = context.getSharedPreferences("ktmoc_ai", Context.MODE_PRIVATE)
    
    /**
     * Get stored API key (optional - some models work without it)
     */
    fun getApiKey(): String? = preferences.getString("hf_api_key", null)
    
    /**
     * Store API key for premium models
     */
    fun setApiKey(key: String) {
        preferences.edit().putString("hf_api_key", key).apply()
    }
    
    /**
     * Check if API key is configured
     */
    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()
    
    /**
     * Generate a shim/renderer for unknown data types
     */
    suspend fun generateShim(dataType: String, sampleData: String): String = withContext(Dispatchers.IO) {
        val prompt = """Create a JavaScript function to render $dataType data as HTML for a CRT terminal display.
The function should be named 'render' and accept 'data' as parameter.
Return ONLY valid JavaScript code, no explanations.
Data sample: ${sampleData.take(300)}

Example output format:
function render(data) {
  return '<div style="color:#0f0;">' + data + '</div>';
}
""".trimIndent()
        
        try {
            val response = callModel(MODEL_CODE_GEN, prompt)
            extractFunctionFromResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Shim generation failed", e)
            createDefaultShim(dataType, sampleData)
        }
    }
    
    /**
     * Gather documentation for a service automatically
     */
    suspend fun gatherDocumentation(serviceName: String): String = withContext(Dispatchers.IO) {
        val prompt = """Provide concise technical documentation for the service: $serviceName
Include:
1. Purpose and main functionality
2. Key API endpoints or methods
3. Common use cases
4. Integration requirements

Format as markdown with clear sections. Keep it under 500 words."""
        
        try {
            callModel(MODEL_TEXT_GEN, prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Documentation gathering failed", e)
            "Documentation unavailable for: $serviceName"
        }
    }
    
    /**
     * Create integration hooks for a service
     */
    suspend fun createIntegrationHooks(serviceName: String, apiSpec: String): String = withContext(Dispatchers.IO) {
        val prompt = """Create Kotlin integration hooks for service: $serviceName
Based on this API specification:
${apiSpec.take(500)}

Generate:
1. Service interface definition
2. Data classes for requests/responses
3. Example usage code

Return only Kotlin code, no explanations."""
        
        try {
            callModel(MODEL_CODE_GEN, prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Integration hook creation failed", e)
            "// Integration hooks unavailable"
        }
    }
    
    /**
     * Explain code or configuration
     */
    suspend fun explainCode(code: String): String = withContext(Dispatchers.IO) {
        val prompt = """Explain this code concisely:
$code

Provide:
1. What it does
2. Key components
3. Potential issues or improvements

Keep explanation under 200 words."""
        
        try {
            callModel(MODEL_CODE_EXPLAIN, prompt)
        } catch (e: Exception) {
            Log.e(TAG, "Code explanation failed", e)
            "Explanation unavailable"
        }
    }
    
    /**
     * Call Hugging Face inference API
     */
    private suspend fun callModel(modelId: String, input: String): String = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/$modelId"
        val apiKey = getApiKey()
        
        val headers = mutableMapOf(
            "Content-Type" to "application/json"
        )
        
        if (!apiKey.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $apiKey"
        }
        
        val requestBody = JSONObject().apply {
            put("inputs", input)
            put("parameters", JSONObject().apply {
                put("max_new_tokens", 512)
                put("temperature", 0.3)
                put("return_full_text", false)
            })
        }
        
        try {
            // Using Java HttpURLConnection since we're in Android
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            connection.doOutput = true
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseHFResponse(response)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "API error $responseCode: $errorBody")
                throw Exception("API error: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network error", e)
            throw e
        }
    }
    
    /**
     * Parse Hugging Face response
     */
    private fun parseHFResponse(response: String): String {
        return try {
            val jsonArray = org.json.JSONArray(response)
            if (jsonArray.length() > 0) {
                val obj = jsonArray.getJSONObject(0)
                obj.optString("generated_text", "")
            } else {
                ""
            }
        } catch (e: Exception) {
            // Try parsing as plain text
            response
        }
    }
    
    /**
     * Extract function from AI response
     */
    private fun extractFunctionFromResponse(response: String): String {
        // Look for function definition
        val functionPattern = Regex("""function\s+render\s*\([^)]*\)\s*\{[\s\S]*?\}""", RegexOption.DOT_MATCHES_ALL)
        val match = functionPattern.find(response)
        return match?.value ?: createDefaultShimFromResponse(response)
    }
    
    /**
     * Create default shim from response
     */
    private fun createDefaultShimFromResponse(response: String): String {
        return """
            <div style="background: #001a00; padding: 8px; border: 1px dashed #f0f;">
                <div style="color: #0f0;">${response.take(300).replace("<", "&lt;").replace(">", "&gt;")}</div>
            </div>
        """.trimIndent()
    }
    
    /**
     * Create basic default shim
     */
    private fun createDefaultShim(dataType: String, sampleData: String): String {
        return """
            <div style="background: #001a00; padding: 8px; border: 1px dashed #f0f; 
                        border-radius: 2px; font-family: monospace;">
                <div style="color: #f0f; margin-bottom: 4px;">⬡ TYPE: $dataType</div>
                <div style="color: #060; font-size: 10px; overflow-x: auto;">
                    ${sampleData.take(200).replace("<", "&lt;").replace(">", "&gt;")}...
                </div>
            </div>
        """.trimIndent()
    }
    
    /**
     * Get available models list
     */
    suspend fun getAvailableModels(): List<String> = withContext(Dispatchers.IO) {
        // This would query Hugging Face API for available models
        // For now, return hardcoded list
        listOf(
            MODEL_CODE_GEN,
            MODEL_TEXT_GEN,
            MODEL_DOC_SUMMARY,
            MODEL_CODE_EXPLAIN
        )
    }
    
    /**
     * Test connection to Hugging Face
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            callModel(MODEL_TEXT_GEN, "Test")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }
    
    /**
     * Clear stored API key
     */
    fun clearApiKey() {
        preferences.edit().remove("hf_api_key").apply()
    }
    
    /**
     * Get model info
     */
    suspend fun getModelInfo(modelId: String): String = withContext(Dispatchers.IO) {
        gatherDocumentation(modelId.substringAfterLast('/'))
    }
}
