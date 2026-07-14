package com.ktmoc.nexus

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ktmoc.nexus.ai.HuggingFaceService
import com.ktmoc.nexus.skeuomorphic.SkeuomorphicEngine
import com.ktmoc.nexus.view.ViewFactory
import kotlinx.coroutines.*
import org.json.JSONObject

/**
 * KTMOC NEXUS v4.1 - Main Activity
 * Integrates skeuomorphic transformations, dynamic views, AI shims, and WebUSB
 */
class NexusActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "NexusActivity"
        private const val ASSET_URL = "file:///android_asset/nexus.html"
    }
    
    private lateinit var webView: WebView
    private lateinit var chatInput: EditText
    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var viewCountText: TextView
    
    private lateinit var viewFactory: ViewFactory
    private lateinit var aiService: HuggingFaceService
    private lateinit var skeuomorphicEngine: SkeuomorphicEngine
    
    private val nexusScope = CoroutineScope(Dispatchers.Main + Job())
    private var geminiKey: String? = null
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize services
        viewFactory = ViewFactory(this)
        aiService = HuggingFaceService(this)
        skeuomorphicEngine = SkeuomorphicEngine(this)
        
        // Setup UI
        setContentView(R.layout.activity_nexus)
        
        webView = findViewById(R.id.webView)
        chatInput = findViewById(R.id.chatInput)
        statusText = findViewById(R.id.statusText)
        modeText = findViewById(R.id.modeText)
        viewCountText = findViewById(R.id.viewCountText)
        
        val sendButton: ImageButton = findViewById(R.id.sendButton)
        sendButton.setOnClickListener { sendChat() }
        
        // Configure WebView
        setupWebView()
        
        // Load interface
        loadNexusInterface()
        
        // Setup skeuomorphic updates
        skeuomorphicEngine.addListener { state ->
            updateUIForState(state)
        }
        
        Log.i(TAG, "KTMOC NEXUS v4.1 initialized")
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectJavaScriptInterface()
                updateStatus("READY")
                skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.SUCCESS, 1f, 0.8f)
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                Log.d("WebView", consoleMessage.message())
                return true
            }
        }
        
        // Add JavaScript interface for Android-native communication
        webView.addJavascriptInterface(WebAppInterface(), "AndroidInterface")
    }
    
    private fun loadNexusInterface() {
        try {
            val htmlContent = assets.open("nexus.html").bufferedReader().use { it.readText() }
            webView.loadDataWithBaseURL(ASSET_URL, htmlContent, "text/html", "UTF-8", null)
            skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.CONNECTING, 0.3f)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load interface", e)
            Toast.makeText(this, "Failed to load interface", Toast.LENGTH_SHORT).show()
            skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.ERROR, 1f, 1f)
        }
    }
    
    private fun injectJavaScriptInterface() {
        val jsCode = """
            window.AndroidBridge = {
                parseText: function(text) {
                    return AndroidInterface.parseText(text);
                },
                detectType: function(text) {
                    return AndroidInterface.detectType(text);
                },
                generateShim: function(type, data) {
                    return AndroidInterface.generateShim(type, data);
                },
                updateStatus: function(status) {
                    AndroidInterface.updateStatus(status);
                },
                log: function(message, level) {
                    AndroidInterface.log(message, level);
                }
            };
            
            console.log('Android Bridge initialized');
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode, null)
    }
    
    private fun sendChat() {
        val message = chatInput.text.toString().trim()
        if (message.isEmpty()) return
        
        chatInput.text.clear()
        
        nexusScope.launch {
            skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.PROCESSING, 0.5f)
            
            try {
                if (message.startsWith("/")) {
                    handleCommand(message)
                } else {
                    // Send to AI
                    val response = aiService.gatherDocumentation(message)
                    postMessageToChat("AI", response)
                }
                
                skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.SUCCESS, 1f, 0.7f)
            } catch (e: Exception) {
                Log.e(TAG, "Chat error", e)
                postMessageToChat("SYSTEM", "Error: ${e.message}")
                skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.ERROR, 1f, 1f)
            }
        }
    }
    
    private suspend fun handleCommand(command: String) {
        when {
            command == "/help" -> {
                postMessageToChat("SYSTEM", """
                    Available commands:
                    /help - Show this help
                    /parse - Parse input text
                    /detect - Detect file type
                    /clear - Clear all
                    /shim <type> - Generate shim for type
                """.trimIndent())
            }
            command == "/parse" -> {
                val text = chatInput.hint.toString()
                if (text.isNotEmpty()) {
                    parseText(text)
                }
            }
            command == "/detect" -> {
                // Trigger detection
                webView.evaluateJavascript("detectFile()", null)
            }
            command == "/clear" -> {
                webView.evaluateJavascript("clrAll()", null)
            }
            command.startsWith("/shim") -> {
                val parts = command.split(" ")
                if (parts.size > 1) {
                    val type = parts[1]
                    generateShim(type, "Sample data")
                }
            }
        }
    }
    
    private fun parseText(text: String) {
        nexusScope.launch {
            skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.ANALYZING, 0.5f)
            
            val detectedType = viewFactory.detectType(text)
            modeText.text = detectedType
            
            val html = viewFactory.createView(detectedType, text)
            
            // Update output in WebView
            val jsCode = """
                document.getElementById('txt-out').innerHTML = `$html`;
            """.trimIndent()
            
            webView.evaluateJavascript(jsCode) {
                skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.SUCCESS, 1f, 0.8f)
                Toast.makeText(this@NexusActivity, "Parsed as $detectedType", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun generateShim(type: String, data: String) {
        nexusScope.launch {
            skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.PROCESSING, 0.3f)
            
            val shim = aiService.generateShim(type, data)
            
            val jsCode = """
                document.getElementById('txt-out').innerHTML = `$shim`;
            """.trimIndent()
            
            webView.evaluateJavascript(jsCode) {
                skeuomorphicEngine.updateState(SkeuomorphicEngine.TaskType.SUCCESS, 1f, 0.9f)
                Toast.makeText(this@NexusActivity, "Shim generated", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun postMessageToChat(sender: String, message: String) {
        val escapedMessage = message.replace("`", "\\`").replace("$", "\\$")
        val jsCode = """
            (function() {
                var msgDiv = document.createElement('div');
                msgDiv.className = 'cht-msg';
                msgDiv.innerHTML = '<span class="cht-${sender.lowercase()}">${sender}:</span> $escapedMessage';
                document.getElementById('cht-log').appendChild(msgDiv);
                document.getElementById('cht-log').scrollTop = document.getElementById('cht-log').scrollHeight;
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(jsCode, null)
    }
    
    private fun updateUIForState(state: SkeuomorphicEngine.TransformState) {
        runOnUiThread {
            statusText.text = state.taskType.name
            viewCountText.text = state.activeElements.size.toString()
            
            // Apply CSS variables to WebView
            val cssVars = skeuomorphicEngine.getCSSVariables()
            var jsCode = ""
            cssVars.forEach { (key, value) ->
                jsCode += "document.documentElement.style.setProperty('$key', '$value');\n"
            }
            
            webView.evaluateJavascript(jsCode, null)
        }
    }
    
    private fun updateStatus(status: String) {
        runOnUiThread {
            statusText.text = status
        }
    }
    
    // Inner class for JavaScript interface
    inner class WebAppInterface {
        @android.webkit.JavascriptInterface
        fun parseText(text: String): String {
            val type = viewFactory.detectType(text)
            return JSONObject().apply {
                put("type", type)
                put("length", text.length)
            }.toString()
        }
        
        @android.webkit.JavascriptInterface
        fun detectType(text: String): String {
            return viewFactory.detectType(text)
        }
        
        @android.webkit.JavascriptInterface
        fun generateShim(type: String, data: String): String {
            return runBlocking {
                aiService.generateShim(type, data)
            }
        }
        
        @android.webkit.JavascriptInterface
        fun updateStatus(status: String) {
            runOnUiThread {
                statusText.text = status
            }
        }
        
        @android.webkit.JavascriptInterface
        fun log(message: String, level: String) {
            when (level) {
                "error" -> Log.e("NexusJS", message)
                "warn" -> Log.w("NexusJS", message)
                "info" -> Log.i("NexusJS", message)
                else -> Log.d("NexusJS", message)
            }
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        nexusScope.cancel()
        skeuomorphicEngine.reset()
    }
}
