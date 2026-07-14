package com.ktmoc.nexus.view

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Dynamic View Factory - Creates skeuomorphic views based on data type
 * Transforms UI as tasking changes, supporting text, JSON, CSV, and custom shims
 */
class ViewFactory(private val context: Context) {
    
    private val renderers = mutableMapOf<String, (String) -> String>()
    private val loadedShims = mutableMapOf<String, String>()
    
    init {
        // Register default renderers
        registerRenderer("text", ::renderText)
        registerRenderer("json", ::renderJSON)
        registerRenderer("csv", ::renderCSV)
        registerRenderer("hex", ::renderHex)
        registerRenderer("log", ::renderLog)
    }
    
    /**
     * Register a custom renderer for a data type
     */
    fun registerRenderer(type: String, renderer: (String) -> String) {
        renderers[type] = renderer
    }
    
    /**
     * Detect data type from content
     */
    fun detectType(data: String): String {
        val trimmed = data.trim()
        
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
            trimmed.contains(",") && trimmed.contains("\n") && trimmed.lines().size > 2 -> "csv"
            trimmed.matches(Regex("^[0-9A-Fa-f\\s]+$")) && trimmed.length > 20 -> "hex"
            trimmed.contains("ERROR") || trimmed.contains("WARN") || trimmed.contains("INFO") -> "log"
            else -> "text"
        }
    }
    
    /**
     * Create a view for the given data
     */
    suspend fun createView(type: String, data: String): String {
        return if (renderers.containsKey(type)) {
            renderers[type]!!.invoke(data)
        } else {
            // Try to generate a shim for unknown types
            generateShim(type, data)
        }
    }
    
    /**
     * Render plain text with CRT styling
     */
    private fun renderText(data: String): String {
        val escaped = escapeHtml(data.take(5000))
        return """
            <div style="font-family: 'VT323', monospace; font-size: 12px; color: #0f0; 
                        background: #000a00; padding: 8px; overflow-x: auto; white-space: pre-wrap;
                        border: 1px solid #030; border-radius: 2px;">
            $escaped
            </div>
        """.trimIndent()
    }
    
    /**
     * Render JSON with syntax highlighting and line numbers
     */
    private fun renderJSON(data: String): String {
        return try {
            val formatted = formatJSON(data)
            val lines = formatted.split("\n")
            val highlightedLines = lines.mapIndexed { index, line ->
                val colored = colorizeJSON(line)
                "<div style='line-height: 1.4;'><span style='color: #060; margin-right: 8px; user-select: none;'>${index + 1}</span>$colored</div>"
            }
            """
                <div style="font-family: 'VT323', monospace; font-size: 11px; 
                            background: #000a00; padding: 8px; max-height: 400px; 
                            overflow-y: auto; border: 1px solid #0ff; border-radius: 2px;">
                ${highlightedLines.joinToString("")}
                </div>
            """.trimIndent()
        } catch (e: Exception) {
            "<div style='color: #f00; padding: 8px;'>Invalid JSON: ${e.message}</div>"
        }
    }
    
    /**
     * Render CSV as styled table
     */
    private fun renderCSV(data: String): String {
        val lines = data.trim().split("\n")
        if (lines.isEmpty()) return "<div style='color: #f00;'>Empty CSV</div>"
        
        val headers = lines[0].split(",")
        val headerRow = headers.joinToString("") { 
            "<th style='padding: 4px 8px; background: #001a00; color: #0ff; border: 1px solid #030; text-align: left;'>${escapeHtml(it)}</th>" 
        }
        
        val dataRows = lines.drop(1).map { line ->
            val cells = line.split(",").joinToString("") { 
                "<td style='padding: 4px 8px; border: 1px solid #030; color: #0f0;'>${escapeHtml(it)}</td>" 
            }
            "<tr>$cells</tr>"
        }.joinToString("")
        
        return """
            <div style="overflow-x: auto; max-height: 400px; overflow-y: auto;">
            <table style="border-collapse: collapse; font-family: 'VT323', monospace; font-size: 10px; width: 100%;">
                <thead><tr>$headerRow</tr></thead>
                <tbody>$dataRows</tbody>
            </table>
            </div>
        """.trimIndent()
    }
    
    /**
     * Render hex dump with addresses
     */
    private fun renderHex(data: String): String {
        val cleaned = data.replace("\\s+".toRegex(), "").uppercase()
        val lines = mutableListOf<String>()
        
        for (i in cleaned.indices step 32) {
            val chunk = cleaned.substring(i, minOf(i + 32, cleaned.length))
            val address = i.toString(16).padStart(8, '0').uppercase()
            val hexPairs = chunk.chunked(2).joinToString(" ")
            val ascii = chunk.chunked(2).joinToString("") { pair ->
                val code = pair.toIntOrNull(16) ?: 0
                if (code in 32..126) code.toChar().toString() else "."
            }
            lines.add("<div style='font-family: monospace; font-size: 10px; line-height: 1.4;'>")
            lines.add("<span style='color: #0ff;'>$address</span>  ")
            lines.add("<span style='color: #0f0;'>${hexPairs.padEnd(47)}</span>  ")
            lines.add("<span style='color: #ff0;'>|$ascii|</span>")
            lines.add("</div>")
        }
        
        return """
            <div style="background: #000a00; padding: 8px; border: 1px solid #030; 
                        max-height: 400px; overflow-y: auto; font-family: 'VT323', monospace;">
            ${lines.joinToString("")}
            </div>
        """.trimIndent()
    }
    
    /**
     * Render log files with level-based coloring
     */
    private fun renderLog(data: String): String {
        val lines = data.split("\n")
        val coloredLines = lines.map { line ->
            val colored = when {
                line.contains("ERROR", ignoreCase = true) -> "<span style='color: #f00;'>$line</span>"
                line.contains("WARN", ignoreCase = true) -> "<span style='color: #ff0;'>$line</span>"
                line.contains("INFO", ignoreCase = true) -> "<span style='color: #0ff;'>$line</span>"
                line.contains("DEBUG", ignoreCase = true) -> "<span style='color: #0f0;'>$line</span>"
                else -> "<span style='color: #060;'>$line</span>"
            }
            "<div style='font-size: 9px; line-height: 1.3; margin-bottom: 1px;'>$colored</div>"
        }
        
        return """
            <div style="background: #000a00; padding: 8px; border: 1px solid #030; 
                        max-height: 400px; overflow-y: auto; font-family: 'VT323', monospace;">
            ${coloredLines.joinToString("")}
            </div>
        """.trimIndent()
    }
    
    /**
     * Generate a shim for unknown data types using AI
     */
    private suspend fun generateShim(type: String, data: String): String {
        // This would call the AI service to generate a custom renderer
        // For now, return a basic placeholder
        val sample = data.take(200).replace("<", "&lt;").replace(">", "&gt;")
        return """
            <div style="background: #001a00; padding: 8px; border: 1px dashed #f0f; 
                        border-radius: 2px; font-family: 'VT323', monospace;">
                <div style="color: #f0f; margin-bottom: 4px;">⬡ CUSTOM TYPE: ${escapeHtml(type)}</div>
                <div style="color: #060; font-size: 10px; overflow-x: auto;">$sample...</div>
                <div style="color: #030; font-size: 9px; margin-top: 4px;">
                    [Shim generator available via AI integration]
                </div>
            </div>
        """.trimIndent()
    }
    
    /**
     * Format JSON string with proper indentation
     */
    private fun formatJSON(json: String): String {
        return when {
            json.trim().startsWith("{") -> {
                JSONObject(json).toString(2)
            }
            json.trim().startsWith("[") -> {
                JSONArray(json).toString(2)
            }
            else -> json
        }
    }
    
    /**
     * Add syntax highlighting to JSON line
     */
    private fun colorizeJSON(line: String): String {
        var result = line
            .replace("\"", "<span style='color: #0ff;'>\"</span>")
            .replace(": ", ": <span style='color: #ff0;'>")
            .replace(",", "</span>,")
        
        // Color keywords
        result = result.replace("null", "<span style='color: #f0f;'>null</span>")
            .replace("true", "<span style='color: #0f0;'>true</span>")
            .replace("false", "<span style='color: #f00;'>false</span>")
        
        return result
    }
    
    /**
     * Escape HTML special characters
     */
    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
    
    /**
     * Store a generated shim for later use
     */
    fun storeShim(type: String, shimCode: String) {
        loadedShims[type] = shimCode
        // Could evaluate and register as a new renderer
    }
    
    /**
     * Get all registered view types
     */
    fun getRegisteredTypes(): List<String> = renderers.keys.toList()
    
    /**
     * Clear all stored shims
     */
    fun clearShims() {
        loadedShims.clear()
    }
}
