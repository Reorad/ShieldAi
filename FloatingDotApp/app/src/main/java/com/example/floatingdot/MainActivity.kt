package com.example.floatingdot

import android.content.Intent
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

class MainActivity : AppCompatActivity() {

    private var isAskingPermission = false
    private lateinit var textClassifier: TextClassifier

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isAskingPermission = false
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permission denied. App cannot work fully.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
            Toast.makeText(this, "Failed to initialize classifier: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupUI() {
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val classifyButton = findViewById<Button>(R.id.classifyButton)
        val resultText = findViewById<TextView>(R.id.resultText)

        classifyButton.setOnClickListener {
            if (!::textClassifier.isInitialized) {
                Toast.makeText(this, "Classifier not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val inputText = messageInput.text.toString()
            if (inputText.isNotEmpty()) {
                val classifierResult = textClassifier.classify(inputText)
                val topCategory = classifierResult.classificationResult().classifications()[0].categories()[0]
                val resultString = "Result: ${topCategory.categoryName()} (${String.format("%.2f", topCategory.score())})"
                resultText.text = resultString
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStart() {
        super.onStart()
        // Stop service when app is in foreground
        val intent = Intent(this, FloatingWidgetService::class.java)
        stopService(intent)
    }

    override fun onStop() {
        super.onStop()
        // Start service when app goes to background, unless we are just opening settings
        if (!isAskingPermission && Settings.canDrawOverlays(this)) {
            val intent = Intent(this, FloatingWidgetService::class.java)
            try {
                startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
