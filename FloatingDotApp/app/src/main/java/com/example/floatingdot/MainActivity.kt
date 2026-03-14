package com.example.floatingdot

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
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

class MainActivity : AppCompatActivity() {

    private var isAskingPermission = false
    private lateinit var textClassifier: TextClassifier
    private lateinit var projectionManager: MediaProjectionManager

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isAskingPermission = false
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, FloatingWidgetService::class.java).apply {
                putExtra("projection_data", result.data)
                putExtra("result_code", result.resultCode)
            }
            startService(serviceIntent)
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        checkOverlayPermission()
        setupClassifier()
        setupUI()
    }

    private fun setupClassifier() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("bert_classifier.tflite")
            
            val optionsBuilder = TextClassifierOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
            
            val options = optionsBuilder.build()
            textClassifier = TextClassifier.createFromOptions(this, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupUI() {
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val classifyButton = findViewById<Button>(R.id.classifyButton)
        val resultText = findViewById<TextView>(R.id.resultText)
        val startBubbleButton = findViewById<Button>(R.id.startBubbleButton)

        classifyButton.setOnClickListener {
            if (!::textClassifier.isInitialized) {
                Toast.makeText(this, "Classifier not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val inputText = messageInput.text.toString()
            if (inputText.isNotEmpty()) {
                val classifierResult = textClassifier.classify(inputText)
                val topCategory = classifierResult.classificationResult().classifications()[0].categories()[0]
                
                val isScam = topCategory.categoryName().equals("Negative", ignoreCase = true) || 
                             inputText.contains("win", ignoreCase = true) || 
                             inputText.contains("prize", ignoreCase = true) ||
                             inputText.contains("urgent", ignoreCase = true)

                val resultString = if (isScam) {
                    "Result: SCAM DETECTED! (${String.format(Locale.US, "%.2f", topCategory.score())})"
                } else {
                    "Result: Safe (${topCategory.categoryName()})"
                }
                resultText.text = resultString
            }
        }

        startBubbleButton.setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
            } else {
                checkOverlayPermission()
            }
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            isAskingPermission = true
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }
}
