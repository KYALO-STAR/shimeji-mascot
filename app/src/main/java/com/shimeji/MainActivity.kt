package com.shimeji

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.TextView
import kotlin.math.sin

class MainActivity : Activity() {
    private var overlayView: OverlayView? = null
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
    private var physicsRunning = false
    private lateinit var windowManager: WindowManager

    companion object {
        const val SIZE = 120
        private const val GRAVITY = 0.5f
        private const val BOUNCE = 0.6f
        private const val FRICTION = 0.98f
    }

    inner class OverlayView(context: android.content.Context) : android.view.View(context) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val tv = TextView(this).apply {
                text = "Tap to grant overlay permission"
                textSize = 18f
                setOnClickListener {
                    startActivityForResult(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        ),
                        1001
                    )
                }
            }
            setContentView(tv)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }

        showOverlay()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) showOverlay()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                showOverlay()
            } else {
                val tv = TextView(this).apply {
                    text = "Overlay permission required"
                    textSize = 18f
                }
                setContentView(tv)
            }
        }
    }

    private fun showOverlay() {
        overlayView = OverlayView(this)
        overlayView?.layoutParams = ViewGroup.LayoutParams(SIZE, SIZE)

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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

        overlayView?.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        try {
            windowManager.addView(overlayView, params)
        } catch (e: Exception) {
            setContentView(TextView(this).apply {
                text = "Overlay failed: ${e.message}"
                textSize = 14f
            })
            return
        }

        startPhysicsLoop()

        val tv = TextView(this).apply {
            text = "Shimeji is running!"
            textSize = 18f
        }
        setContentView(tv)
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
        if (physicsRunning) return
        physicsRunning = true
        Thread {
            while (physicsRunning) {
                try {
                    if (!isDragging) {
                        velY += GRAVITY
                        velX *= FRICTION
                        velY *= FRICTION
                        posX += velX
                        posY += velY

                        val metrics = android.util.DisplayMetrics()
                        windowManager.defaultDisplay.getMetrics(metrics)
                        val screenW = metrics.widthPixels
                        val screenH = metrics.heightPixels

                        if (posX < 0) { posX = 0f; velX = -velX * BOUNCE }
                        if (posX > screenW - SIZE) { posX = (screenW - SIZE).toFloat(); velX = -velX * BOUNCE }
                        if (posY < 0) { posY = 0f; velY = -velY * BOUNCE }
                        if (posY > screenH - SIZE) { posY = (screenH - SIZE).toFloat(); velY = -velY * BOUNCE }

                        updatePosition()
                    }

                    time += 0.05f
                    frameCount++
                    overlayView?.postInvalidate()
                } catch (_: Exception) {}
                Thread.sleep(16)
            }
        }.apply { isDaemon = true }.start()
    }

    private fun updatePosition() {
        params?.let { p ->
            p.x = posX.toInt()
            p.y = posY.toInt()
            try {
                overlayView?.post { params?.let { windowManager.updateViewLayout(overlayView, it) } }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        physicsRunning = false
        overlayView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
    }
}
