package com.example.floatingdot

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier.TextClassifierOptions
import java.util.Locale
import android.util.Patterns
import android.util.Log

class MainActivity : AppCompatActivity() {

    private var isAskingPermission = false
    private var smsClassifier: TextClassifier? = null
    private var phishingClassifier: TextClassifier? = null
    private lateinit var sharedPreferences: SharedPreferences

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isAskingPermission = false
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("ShieldAISettings", Context.MODE_PRIVATE)

        checkOverlayPermission()
        setupClassifiers()
        setupUI()
    }

    private fun setupClassifiers() {
        // Safe loading with fallback to bert_classifier if needed
        smsClassifier = loadClassifier("sms_spam_model.tflite") ?: loadClassifier("bert_classifier.tflite")
        phishingClassifier = loadClassifier("phishing_model.tflite") ?: loadClassifier("bert_classifier.tflite")

        if (smsClassifier == null && phishingClassifier == null) {
            Toast.makeText(this, "Error: AI models not loaded", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadClassifier(modelName: String): TextClassifier? {
        return try {
            val options = TextClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelName).build())
                .build()
            TextClassifier.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e("ShieldAI", "Failed to load $modelName: ${e.message}")
            null
        }
    }

    fun extractUrls(message: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = Patterns.WEB_URL.matcher(message)
        while (matcher.find()) {
            urls.add(matcher.group())
        }
        return urls
    }

    private fun setupUI() {
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val classifyButton = findViewById<Button>(R.id.classifyButton)
        val resultText = findViewById<TextView>(R.id.resultText)
        val startBubbleButton = findViewById<Button>(R.id.startBubbleButton)
        
        val safeBrowsingService = SafeBrowsingService()

        classifyButton.setOnClickListener {
            val inputText = messageInput.text.toString()
            if (inputText.isEmpty()) return@setOnClickListener

            if (smsClassifier == null && phishingClassifier == null) {
                Toast.makeText(this, "AI Engine not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Run AI Classifications
            var isSmsScam = false
            var isPhishingScam = false
            var maxScore = 0.0f

            smsClassifier?.let {
                val result = it.classify(inputText)
                val top = result.classificationResult().classifications()[0].categories()[0]
                isSmsScam = top.categoryName() == "1" || top.categoryName().equals("Negative", ignoreCase = true)
                maxScore = top.score()
            }

            phishingClassifier?.let {
                val result = it.classify(inputText)
                val top = result.classificationResult().classifications()[0].categories()[0]
                isPhishingScam = top.categoryName() == "1" || top.categoryName().equals("Negative", ignoreCase = true)
                if (top.score() > maxScore) maxScore = top.score()
            }

            val isAiScam = isSmsScam || isPhishingScam
            val isScam = isAiScam || 
                         inputText.contains("win", ignoreCase = true) ||
                         inputText.contains("prize", ignoreCase = true) ||
                         inputText.contains("urgent", ignoreCase = true)

            // 2. Display AI Result
            val detectionSource = when {
                isSmsScam && isPhishingScam -> "Dual AI"
                isSmsScam -> "SMS AI"
                isPhishingScam -> "Phishing AI"
                else -> "Keyword"
            }

            var finalResultText = if (isScam) {
                "Result: SCAM DETECTED! \n($detectionSource: ${String.format(Locale.US, "%.0f%%", maxScore * 100)})"
            } else {
                "Result: Safe (AI Confidence: ${String.format(Locale.US, "%.0f%%", maxScore * 100)})"
            }
            resultText.text = finalResultText

            // 3. URL Verification (Restored the good/fake logic)
            val urls = extractUrls(inputText)
            if (urls.isNotEmpty()) {
                var urlsVerified = 0
                var threatFoundInBatch = false
                for (url in urls) {
                    safeBrowsingService.checkUrl(url) { isThreat ->
                        runOnUiThread {
                            urlsVerified++
                            if (isThreat) {
                                threatFoundInBatch = true
                                Toast.makeText(this, "⚠️ Unsafe URL detected", Toast.LENGTH_LONG).show()
                                resultText.text = "${resultText.text}\n❌ Threat found in link!"
                            } else if (urlsVerified == urls.size && !threatFoundInBatch) {
                                // Only show "Safe" toast if ALL urls in this batch are safe
                                Toast.makeText(this, "✅ URLs are safe", Toast.LENGTH_LONG).show()
                                resultText.text = "${resultText.text}\n✅ Links verified safe."
                            }
                        }
                    }
                }
            }
        }

        startBubbleButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, FloatingWidgetService::class.java))
                Toast.makeText(this, "Shield Widget Deployed", Toast.LENGTH_SHORT).show()
            } else {
                checkOverlayPermission()
            }
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            isAskingPermission = true
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }
}
