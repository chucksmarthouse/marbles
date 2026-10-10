package com.aboratech.marbles

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val maze = Maze.DEFAULT
    private val tiltSensor = TiltSensor(context)
    private val animator = TimeAnimator()
    private val prefs = context.getSharedPreferences("marbles", Context.MODE_PRIVATE)

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val fallSoundId = soundPool.load(context, R.raw.fall, 1)
    private val fireworksSoundId = soundPool.load(context, R.raw.fireworks, 1)

    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private lateinit var wallRects: List<RectF>
    private lateinit var marble: Marble
    private lateinit var holeCenters: List<Pair<Float, Float>>
    private var goalX = 0f
    private var goalY = 0f
    private var goalRadius = 0f
    private var holeRadius = 0f
    private var startX = 0f
    private var startY = 0f
    private var won = false
    private var ready = false

    private var runElapsedSeconds = 0f
    private var fellThisRun = false
    private var lastRunSeconds = 0f
    private var bestSeconds: Float? = prefs.getFloat(PREF_BEST_SECONDS, -1f).let { if (it < 0f) null else it }
    private var winStreak = 0
    private var showFireworks = false
    private var fireworksElapsed = 0f

    private val bgPaint = Paint().apply { color = Color.rgb(0x18, 0x1c, 0x2a) }
    private val wallPaint = Paint().apply { color = Color.rgb(0x3a, 0x4a, 0x6a) }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x4c, 0xaf, 0x50) }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
    private val holeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }
    private val marblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xf2, 0xc9, 0x4c) }
    private val marbleHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
    }
    private val overlayPaint = Paint().apply { color = Color.argb(180, 0, 0, 0) }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 64f
    }
    private val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 40f
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.LEFT
        textSize = 36f
    }
    private val starburstPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }
    private val starburstDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starburstColors = intArrayOf(
        Color.rgb(0xff, 0xd7, 0x00),
        Color.rgb(0xff, 0x6b, 0x6b),
        Color.rgb(0x6b, 0xcf, 0xff),
        Color.rgb(0xa0, 0xff, 0x6b),
        Color.rgb(0xff, 0x9f, 0x40),
    )

    init {
        animator.setTimeListener { _, _, deltaMs -> onFrame(deltaMs / 1000f) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cellSize = min(w.toFloat() / maze.cols, h.toFloat() / maze.rows)
        offsetX = (w - cellSize * maze.cols) / 2f
        offsetY = (h - cellSize * maze.rows) / 2f

        val walls = mutableListOf<RectF>()
        maze.forEachWallCell { r, c ->
            val left = offsetX + c * cellSize
            val top = offsetY + r * cellSize
            walls.add(RectF(left, top, left + cellSize, top + cellSize))
        }
        wallRects = walls

        val (startRow, startCol) = maze.startCell()
        val (goalRow, goalCol) = maze.goalCell()
        goalX = offsetX + (goalCol + 0.5f) * cellSize
        goalY = offsetY + (goalRow + 0.5f) * cellSize
        goalRadius = cellSize * 0.35f
        holeRadius = cellSize * 0.35f
        holeBorderPaint.strokeWidth = cellSize * 0.04f

        holeCenters = HOLE_GRID_POSITIONS.map { (r, c) -> (offsetX + c * cellSize) to (offsetY + r * cellSize) }

        val radius = cellSize * 0.3f
        startX = offsetX + (startCol + 0.5f) * cellSize
        startY = offsetY + (startRow + 0.5f) * cellSize
        marble = Marble(startX, startY, radius)
        runElapsedSeconds = 0f
        fellThisRun = false
        ready = true
    }

    private fun onFrame(dt: Float) {
        if (!ready) return
        val clamped = dt.coerceAtMost(0.05f)
        if (!won) {
            update(clamped)
        } else if (showFireworks) {
            fireworksElapsed += clamped
        }
        invalidate()
    }

    private fun update(dt: Float) {
        runElapsedSeconds += dt

        val ax = -tiltSensor.x * ACCEL_SCALE
        val ay = tiltSensor.y * ACCEL_SCALE

        marble.vx += ax * dt
        marble.vy += ay * dt

        val damping = (1f - FRICTION_PER_SECOND * dt).coerceIn(0f, 1f)
        marble.vx *= damping
        marble.vy *= damping

        val speed = sqrt(marble.vx * marble.vx + marble.vy * marble.vy)
        if (speed > MAX_SPEED) {
            val scale = MAX_SPEED / speed
            marble.vx *= scale
            marble.vy *= scale
        }

        marble.x += marble.vx * dt
        marble.y += marble.vy * dt

        resolveCollisions()

        for ((hx, hy) in holeCenters) {
            val hdx = marble.x - hx
            val hdy = marble.y - hy
            if (sqrt(hdx * hdx + hdy * hdy) < holeRadius) {
                marble.place(startX, startY)
                fellThisRun = true
                soundPool.play(fallSoundId, 0.8f, 0.8f, 1, 0, 1f)
                return
            }
        }

        val dx = marble.x - goalX
        val dy = marble.y - goalY
        if (sqrt(dx * dx + dy * dy) < goalRadius) {
            onWin()
        }
    }

    private fun onWin() {
        won = true
        lastRunSeconds = runElapsedSeconds
        val currentBest = bestSeconds
        if (currentBest == null || lastRunSeconds < currentBest) {
            bestSeconds = lastRunSeconds
            prefs.edit().putFloat(PREF_BEST_SECONDS, lastRunSeconds).apply()
        }

        winStreak = if (fellThisRun) 0 else winStreak + 1
        if (winStreak > 0 && winStreak % STREAK_FOR_FIREWORKS == 0) {
            showFireworks = true
            fireworksElapsed = 0f
            soundPool.play(fireworksSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    private fun resolveCollisions() {
        for (rect in wallRects) {
            val closestX = marble.x.coerceIn(rect.left, rect.right)
            val closestY = marble.y.coerceIn(rect.top, rect.bottom)
            val dx = marble.x - closestX
            val dy = marble.y - closestY
            val distSq = dx * dx + dy * dy
            val r = marble.radius
            if (distSq < r * r) {
                val dist = sqrt(distSq.coerceAtLeast(1e-6f))
                val nx = dx / dist
                val ny = dy / dist
                val penetration = r - dist
                marble.x += nx * penetration
                marble.y += ny * penetration

                val vDotN = marble.vx * nx + marble.vy * ny
                if (vDotN < 0f) {
                    marble.vx -= (1f + RESTITUTION) * vDotN * nx
                    marble.vy -= (1f + RESTITUTION) * vDotN * ny
                }
            }
        }
    }

    private fun circleIntersectsRect(cx: Float, cy: Float, r: Float, rect: RectF): Boolean {
        val closestX = cx.coerceIn(rect.left, rect.right)
        val closestY = cy.coerceIn(rect.top, rect.bottom)
        val dx = cx - closestX
        val dy = cy - closestY
        return dx * dx + dy * dy < r * r
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (!ready) return
        for (rect in wallRects) {
            canvas.drawRect(rect, wallPaint)
        }

        // Hole fill only shows where it's actually embedded in a wall; the
        // thin white border traces the hole's full extent regardless.
        for ((hx, hy) in holeCenters) {
            for (rect in wallRects) {
                if (circleIntersectsRect(hx, hy, holeRadius, rect)) {
                    canvas.save()
                    canvas.clipRect(rect)
                    canvas.drawCircle(hx, hy, holeRadius, holePaint)
                    canvas.restore()
                }
            }
            canvas.drawCircle(hx, hy, holeRadius, holeBorderPaint)
        }

        canvas.drawCircle(goalX, goalY, goalRadius, goalPaint)
        canvas.drawCircle(marble.x, marble.y, marble.radius, marblePaint)
        canvas.drawCircle(
            marble.x - marble.radius * 0.3f,
            marble.y - marble.radius * 0.3f,
            marble.radius * 0.35f,
            marbleHighlight,
        )

        if (!won) {
            canvas.drawText(formatTime(runElapsedSeconds), 24f, 56f, hudTextPaint)
            bestSeconds?.let { canvas.drawText("Best: ${formatTime(it)}", 24f, 98f, hudTextPaint) }
        }

        if (won) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            if (showFireworks) drawStarburst(canvas)
            canvas.drawText("You win!", width / 2f, height / 2f - 20f, overlayTextPaint)
            canvas.drawText("Tap to play again", width / 2f, height / 2f + 60f, overlayTextPaint)
            val bestText = bestSeconds?.let { formatTime(it) } ?: formatTime(lastRunSeconds)
            canvas.drawText(
                "Time: ${formatTime(lastRunSeconds)}   Best: $bestText",
                width / 2f,
                height / 2f + 130f,
                statsTextPaint,
            )
            if (winStreak > 0) {
                canvas.drawText("Streak: $winStreak", width / 2f, height / 2f + 180f, statsTextPaint)
            }
        }
    }

    private fun drawStarburst(canvas: Canvas) {
        val cx = width / 2f
        val cy = height * 0.3f
        val rays = 14
        val inner = min(width, height) * 0.05f
        val outerBase = min(width, height) * 0.2f
        for (i in 0 until rays) {
            val angle = fireworksElapsed * 1.0f + (2 * Math.PI * i / rays).toFloat()
            val pulse = 0.75f + 0.25f * sin(fireworksElapsed * 4f + i * 0.7f)
            val outer = outerBase * pulse
            val x1 = cx + cos(angle) * inner
            val y1 = cy + sin(angle) * inner
            val x2 = cx + cos(angle) * outer
            val y2 = cy + sin(angle) * outer
            val color = starburstColors[i % starburstColors.size]
            starburstPaint.color = color
            starburstDotPaint.color = color
            canvas.drawLine(x1, y1, x2, y2, starburstPaint)
            canvas.drawCircle(x2, y2, 10f, starburstDotPaint)
        }
    }

    private fun formatTime(seconds: Float): String {
        val minutes = (seconds / 60).toInt()
        val secs = seconds % 60
        return if (minutes > 0) String.format("%d:%05.2f", minutes, secs) else String.format("%.2fs", secs)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (won && event.action == MotionEvent.ACTION_DOWN) {
            marble.place(startX, startY)
            won = false
            showFireworks = false
            runElapsedSeconds = 0f
            fellThisRun = false
            invalidate()
        }
        return true
    }

    fun resume() {
        tiltSensor.start()
        animator.start()
    }

    fun pause() {
        animator.cancel()
        tiltSensor.stop()
    }

    override fun onDetachedFromWindow() {
        soundPool.release()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val ACCEL_SCALE = 320f
        private const val FRICTION_PER_SECOND = 0.5f
        private const val MAX_SPEED = 1800f
        private const val RESTITUTION = 0.3f
        private const val STREAK_FOR_FIREWORKS = 3
        private const val PREF_BEST_SECONDS = "best_seconds"

        // (row, col) in grid-line units, not cell-center units -- e.g. (3f, 7f)
        // is the grid vertex at the row2/row3 boundary and col6/col7 boundary,
        // not the center of cell (3, 7). This lets a hole sit exactly on a
        // wall corner or wall-floor edge instead of floating in open floor.
        // The first 6 are the maze's inside corners (each cap wall next to a
        // serpentine turn has 2 corners; Maze.DEFAULT has 6 turns, giving 12
        // candidate corners, of which these 6 are the ones adjacent to the
        // open corridor on each side of the turn). The last 3 sit at
        // arbitrary points along a straight wall-floor edge between corners.
        private val HOLE_GRID_POSITIONS = listOf(
            3f to 7f,
            5f to 2f,
            7f to 7f,
            9f to 2f,
            10f to 7f,
            11f to 7f,
            3f to 3.5f,
            8f to 5.5f,
            13f to 5.5f,
        )
    }
}
