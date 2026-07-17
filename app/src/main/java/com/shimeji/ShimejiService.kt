package com.shimeji

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.Gravity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ShimejiService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView
    private var params: WindowManager.LayoutParams? = null

    private var posX = 100f
    private var posY = 300f
    private var velX = 0f
    private var velY = 0f
    private var isDragging = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var time = 0f
    private var frameCount = 0

    companion object {
        const val SIZE = 120
        private const val GRAVITY = 0.5f
        private const val BOUNCE = 0.6f
        private const val FRICTION = 0.98f
    }

    inner class OverlayView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 152, 0)
            isAntiAlias = true
        }
        private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            isAntiAlias = true
        }
        private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(80, 255, 100, 100)
            isAntiAlias = true
        }
        private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 0, 0)
            isAntiAlias = true
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val bob = sin(time * 2f) * 3f

            bodyPaint.color = if (isDragging) Color.rgb(255, 183, 77) else Color.rgb(255, 152, 0)
            canvas.drawCircle(cx, cy + bob, SIZE / 2f - 8, bodyPaint)

            canvas.drawCircle(cx - 20, cy - 10 + bob, 16f, eyePaint)
            canvas.drawCircle(cx + 20, cy - 10 + bob, 16f, eyePaint)
            canvas.drawCircle(cx - 20, cy - 10 + bob, 6f, pupilPaint)
            canvas.drawCircle(cx + 20, cy - 10 + bob, 6f, pupilPaint)

            canvas.drawCircle(cx - 30, cy + 8 + bob, 8f, blushPaint)
            canvas.drawCircle(cx + 30, cy + 8 + bob, 8f, blushPaint)

            canvas.drawArc(cx - 10, cy + 5 + bob, cx + 10, cy + 18 + bob, 0f, 180f, false, mouthPaint)

            if (!isDragging) {
                val blink = (frameCount % 180) < 3
                if (blink) {
                    paint.color = bodyPaint.color
                    canvas.drawRect(cx - 22, cy - 22 + bob, cx - 18, cy - 12 + bob, paint)
                    canvas.drawRect(cx + 18, cy - 22 + bob, cx + 22, cy - 12 + bob, paint)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)
        setupNotification()
        setupOverlay()
    }

    private fun setupNotification() {
        val channelId = "shimeji_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Shimeji", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
                .setContentTitle("Shimeji")
                .setContentText("Your mascot is running")
                .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                .build()
        } else {
            Notification.Builder(this)
                .setContentTitle("Shimeji")
                .setContentText("Your mascot is running")
                .setSmallIcon(android.R.drawable.ic_menu_myplaces)
                .build()
        }
        startForeground(1, notification)
    }

    private fun setupOverlay() {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX.toInt()
            y = posY.toInt()
        }

        overlayView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        overlayView.layoutParams = ViewGroup.LayoutParams(SIZE, SIZE)
        windowManager.addView(overlayView, params)

        startPhysicsLoop()
    }

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                dragOffsetX = event.rawX - posX
                dragOffsetY = event.rawY - posY
                velX = 0f
                velY = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val newX = event.rawX - dragOffsetX
                val newY = event.rawY - dragOffsetY
                velX = newX - posX
                velY = newY - posY
                posX = newX
                posY = newY
                updatePosition()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                if (kotlin.math.abs(velX) < 2f && kotlin.math.abs(velY) < 2f) {
                    velX = 0f
                    velY = 0f
                }
            }
        }
    }

    private fun startPhysicsLoop() {
        Thread {
            while (true) {
                if (!isDragging) {
                    velY += GRAVITY
                    velX *= FRICTION
                    velY *= FRICTION
                    posX += velX
                    posY += velY

                    val display = windowManager.defaultDisplay
                    val displayMetrics = android.util.DisplayMetrics()
                    display.getMetrics(displayMetrics)
                    val screenW = displayMetrics.widthPixels
                    val screenH = displayMetrics.heightPixels

                    if (posX < 0) { posX = 0f; velX = -velX * BOUNCE }
                    if (posX > screenW - SIZE) { posX = (screenW - SIZE).toFloat(); velX = -velX * BOUNCE }
                    if (posY < 0) { posY = 0f; velY = -velY * BOUNCE }
                    if (posY > screenH - SIZE) { posY = (screenH - SIZE).toFloat(); velY = -velY * BOUNCE }

                    updatePosition()
                }

                time += 0.05f
                frameCount++
                overlayView.postInvalidate()
                Thread.sleep(16)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun updatePosition() {
        params?.let { p ->
            p.x = posX.toInt()
            p.y = posY.toInt()
            overlayView.post { windowManager.updateViewLayout(overlayView, p) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
