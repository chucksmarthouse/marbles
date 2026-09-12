package com.aboratech.marbles

import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val maze = Maze.DEFAULT
    private val tiltSensor = TiltSensor(context)
    private val animator = TimeAnimator()

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

    private val bgPaint = Paint().apply { color = Color.rgb(0x18, 0x1c, 0x2a) }
    private val wallPaint = Paint().apply { color = Color.rgb(0x3a, 0x4a, 0x6a) }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x4c, 0xaf, 0x50) }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
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

        holeCenters = HOLE_GRID_POSITIONS.map { (r, c) -> (offsetX + c * cellSize) to (offsetY + r * cellSize) }

        val radius = cellSize * 0.3f
        startX = offsetX + (startCol + 0.5f) * cellSize
        startY = offsetY + (startRow + 0.5f) * cellSize
        marble = Marble(startX, startY, radius)
        ready = true
    }

    private fun onFrame(dt: Float) {
        if (!ready) return
        if (!won) {
            update(dt.coerceAtMost(0.05f))
        }
        invalidate()
    }

    private fun update(dt: Float) {
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
                return
            }
        }

        val dx = marble.x - goalX
        val dy = marble.y - goalY
        if (sqrt(dx * dx + dy * dy) < goalRadius) {
            won = true
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

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        if (!ready) return
        for (rect in wallRects) {
            canvas.drawRect(rect, wallPaint)
        }
        for ((hx, hy) in holeCenters) {
            canvas.drawCircle(hx, hy, holeRadius, holePaint)
        }
        canvas.drawCircle(goalX, goalY, goalRadius, goalPaint)
        canvas.drawCircle(marble.x, marble.y, marble.radius, marblePaint)
        canvas.drawCircle(
            marble.x - marble.radius * 0.3f,
            marble.y - marble.radius * 0.3f,
            marble.radius * 0.35f,
            marbleHighlight,
        )

        if (won) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
            canvas.drawText("You win!", width / 2f, height / 2f - 20f, overlayTextPaint)
            canvas.drawText("Tap to play again", width / 2f, height / 2f + 60f, overlayTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (won && event.action == MotionEvent.ACTION_DOWN) {
            marble.place(startX, startY)
            won = false
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

    companion object {
        private const val ACCEL_SCALE = 320f
        private const val FRICTION_PER_SECOND = 0.5f
        private const val MAX_SPEED = 1800f
        private const val RESTITUTION = 0.3f

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
