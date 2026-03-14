package com.example.floatingdot

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.nio.ByteBuffer
import java.util.regex.Pattern
import kotlin.math.abs

class FloatingWidgetService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var warningView: View
    private lateinit var shieldIcon: View
    private lateinit var sharedPreferences: SharedPreferences
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastTapTime: Long = 0
    private val DOUBLE_TAP_TIMEOUT = 300L
    private var isAutoScanEnabled = false
    private var isScanning = false

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra("update_settings", false) == true) {
            isAutoScanEnabled = intent.getBooleanExtra("auto_scan", false)
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra("result_code", -1) ?: -1
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("projection_data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("projection_data")
        }

        if (resultCode != -1 && resultData != null) {
            startForegroundService()
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            setupImageReader()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundService() {
        val channelId = "floating_dot_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Anti-Scam Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Anti-Scam Protection Active")
            .setSmallIcon(R.drawable.ic_shield_blue)
            .build()
        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("ShieldAISettings", Context.MODE_PRIVATE)
        isAutoScanEnabled = sharedPreferences.getBoolean("auto_scan", false)

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)
        warningView = LayoutInflater.from(this).inflate(R.layout.overlay_warning, null)
        shieldIcon = floatingView.findViewById(R.id.redDotImage)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager.addView(floatingView, params)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        shieldIcon.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) isMoved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        val currentTime = System.currentTimeMillis()
                        if (isAutoScanEnabled) {
                            captureAndScan()
                        } else {
                            if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                                captureAndScan()
                            } else {
                                openApp()
                            }
                        }
                        lastTapTime = currentTime
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openApp() {
        val intentActivity = Intent(this@FloatingWidgetService, MainActivity::class.java)
        intentActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intentActivity)
    }

    private fun setupImageReader() {
        val displayMetrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 2)
        
        imageReader?.setOnImageAvailableListener({ reader ->
            // Do nothing here, we'll acquire manually in captureAndScan
        }, Handler(Looper.getMainLooper()))

        mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            displayMetrics.widthPixels,
            displayMetrics.heightPixels,
            displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    private fun captureAndScan() {
        if (isScanning) return
        isScanning = true
        
        shieldIcon.animate().alpha(0.5f).setDuration(200).start()

        Handler(Looper.getMainLooper()).postDelayed({
            var image = imageReader?.acquireLatestImage()
            
            // If acquireLatestImage fails, try acquireNextImage
            if (image == null) {
                image = imageReader?.acquireNextImage()
            }

            if (image != null) {
                try {
                    val planes = image.planes
                    val buffer: ByteBuffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * image.width
                    
                    val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            analyzeScam(visionText.text)
                            isScanning = false
                            shieldIcon.animate().alpha(1.0f).setDuration(200).start()
                        }
                        .addOnFailureListener {
                            isScanning = false
                            shieldIcon.animate().alpha(1.0f).setDuration(200).start()
                            resetIcon()
                        }
                } catch (e: Exception) {
                    isScanning = false
                    shieldIcon.animate().alpha(1.0f).setDuration(200).start()
                    e.printStackTrace()
                } finally {
                    image.close()
                }
            } else {
                isScanning = false
                shieldIcon.animate().alpha(1.0f).setDuration(200).start()
                Toast.makeText(this, "Screen busy, try again in a second", Toast.LENGTH_SHORT).show()
            }
        }, 300) // Slightly longer delay to ensure buffer is ready
    }

    private fun analyzeScam(text: String) {
        val lowerText = text.lowercase()
        
        val bankScams = listOf("bank", "account", "locked", "suspended", "unauthorized", "login", "transaction", "transfer", "verification", "debit", "credit", "card", "paypal", "zelle", "venmo", "verify your identity")
        val urgencyScams = listOf("urgent", "immediately", "action required", "within 24 hours", "penalty", "lawsuit", "police", "irs", "customs", "arrest")
        val techScams = listOf("virus", "malware", "hacked", "security alert", "microsoft support", "apple support")

        val prizeScams = listOf("won", "winner", "prize", "lottery", "gift card", "reward", "selected", "claim", "congratulations")
        val jobScams = listOf("work from home", "easy money", "crypto", "investment", "passive income", "whatsapp job", "earn money")

        val redKeywords = bankScams + urgencyScams + techScams
        val yellowKeywords = prizeScams + jobScams
        
        val redMatches = redKeywords.count { lowerText.contains(it) }
        val yellowMatches = yellowKeywords.count { lowerText.contains(it) }
        
        val urlPattern = Pattern.compile("(https?://\\S+)")
        val matcher = urlPattern.matcher(lowerText)
        val hasLink = matcher.find()

        if (redMatches >= 1 && hasLink) {
            updateShieldStatus("RED")
            showScamWarning()
        } else if (redMatches >= 1 || (yellowMatches >= 1 && hasLink)) {
            updateShieldStatus("YELLOW")
            Toast.makeText(this, "Caution: Potential Scam Detected", Toast.LENGTH_SHORT).show()
        } else if (yellowMatches >= 1) {
            updateShieldStatus("YELLOW")
            Toast.makeText(this, "Caution: Unusual content", Toast.LENGTH_SHORT).show()
        } else {
            updateShieldStatus("GREEN")
            Toast.makeText(this, "Screen safe", Toast.LENGTH_SHORT).show()
        }
        
        Handler(Looper.getMainLooper()).postDelayed({
            resetIcon()
        }, 4000)
    }

    private fun updateShieldStatus(status: String) {
        when (status) {
            "RED" -> shieldIcon.setBackgroundResource(R.drawable.circle_background_red)
            "YELLOW" -> shieldIcon.setBackgroundResource(R.drawable.circle_background_yellow)
            "GREEN" -> shieldIcon.setBackgroundResource(R.drawable.circle_background_green)
        }
    }

    private fun resetIcon() {
        shieldIcon.setBackgroundResource(R.drawable.circle_background_blue)
    }

    private fun showScamWarning() {
        if (warningView.parent != null) return
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager.addView(warningView, params)
        warningView.findViewById<View>(R.id.closeWarning).setOnClickListener {
            windowManager.removeView(warningView)
            resetIcon()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}
