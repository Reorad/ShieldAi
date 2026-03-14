package com.example.floatingdot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isAskingPermission = false

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
