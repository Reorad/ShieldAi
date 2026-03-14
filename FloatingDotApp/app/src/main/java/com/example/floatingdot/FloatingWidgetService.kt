package com.example.floatingdot

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
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
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastTapTime: Long = 0
    private val DOUBLE_TAP_TIMEOUT = 300L

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)
        warningView = LayoutInflater.from(this).inflate(R.layout.overlay_warning, null)

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager.addView(floatingView, params)

        val shieldIcon = floatingView.findViewById<View>(R.id.redDotImage)
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
                        if (currentTime - lastTapTime < DOUBLE_TAP_TIMEOUT) {
                            // Double Tap -> Scan Screen
                            captureAndScan()
                        } else {
                            // Single Tap -> Open App
                            val intentActivity = Intent(this@FloatingWidgetService, MainActivity::class.java)
                            intentActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            startActivity(intentActivity)
                        }
                        lastTapTime = currentTime
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupImageReader() {
        val displayMetrics = resources.displayMetrics
        imageReader = ImageReader.newInstance(displayMetrics.widthPixels, displayMetrics.heightPixels, PixelFormat.RGBA_8888, 2)
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
        val image = imageReader?.acquireLatestImage()
        if (image != null) {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    analyzeScam(visionText.text)
                }
        } else {
            Toast.makeText(this, "Scanning...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun analyzeScam(text: String) {
        val lowerText = text.lowercase()
        val bankScams = listOf("bank", "account", "locked", "suspended", "unauthorized", "login", "transaction", "transfer", "verification", "debit", "credit", "card", "paypal", "zelle", "venmo")
        val prizeScams = listOf("won", "winner", "prize", "lottery", "gift card", "reward", "selected", "claim", "congratulations")
        val urgencyScams = listOf("urgent", "immediately", "action required", "within 24 hours", "final notice", "penalty", "lawsuit", "police", "irs", "customs")
        val techScams = listOf("virus", "malware", "hacked", "security alert", "system failure", "microsoft support", "apple support")
        val jobScams = listOf("work from home", "easy money", "crypto", "investment", "passive income", "whatsapp job")

        val allKeywords = bankScams + prizeScams + urgencyScams + techScams + jobScams
        val matchCount = allKeywords.count { lowerText.contains(it) }
        val urlPattern = Pattern.compile("(https?://\\S+)")
        val matcher = urlPattern.matcher(lowerText)
        val hasLink = matcher.find()

        var scamScore = 0
        if (matchCount >= 2) scamScore += 40
        if (matchCount >= 4) scamScore += 30
        if (hasLink && matchCount >= 1) scamScore += 50
        val commonScamPhrases = listOf("verify your identity", "click here to", "account has been", "suspicious activity", "gift card for")
        if (commonScamPhrases.any { lowerText.contains(it) }) scamScore += 40

        if (scamScore >= 60) {
            showScamWarning()
        } else {
            Toast.makeText(this, "Screen safe", Toast.LENGTH_SHORT).show()
        }
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaProjection?.stop()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}
