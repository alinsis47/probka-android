package com.probka.game

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.min

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 24, 32)
        window.navigationBarColor = android.graphics.Color.rgb(16, 24, 32)
        window.decorView.systemUiVisibility = 0
        setContentView(ProbkaView(this))
    }
}

private data class Car(
    val id: String,
    var x: Int,
    var y: Int,
    val length: Int,
    val horizontal: Boolean,
    val color: Int,
    val target: Boolean = false
)

private data class Level(val title: String, val cars: List<Car>)

class ProbkaView(context: Context) : View(context) {
    private val bg = 0xFF101820.toInt()
    private val panel = 0xFF1B2732.toInt()
    private val gridColor = 0xFF31414E.toInt()
    private val textColor = 0xFFF4F7FA.toInt()
    private val muted = 0xFF9EADB9.toInt()
    private val accent = 0xFFE84855.toInt()
    private val green = 0xFF27AE60.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("probka_progress", Context.MODE_PRIVATE)

    private val levels = listOf(
        Level("Первый выезд", listOf(
            Car("R", 0, 2, 2, true, 0xFFE84855.toInt(), true),
            Car("A", 2, 0, 2, false, 0xFF2D9CDB.toInt()),
            Car("B", 3, 1, 2, false, 0xFFF2C14E.toInt()),
            Car("C", 4, 1, 2, false, 0xFF27AE60.toInt()),
            Car("D", 0, 0, 2, true, 0xFF9B59B6.toInt()),
            Car("E", 0, 4, 2, true, 0xFF56CCF2.toInt()),
            Car("F", 2, 4, 2, false, 0xFFF2994A.toInt()),
            Car("G", 3, 4, 2, true, 0xFFEB5757.toInt())
        )),
        Level("Плотный поток", listOf(
            Car("R", 0, 2, 2, true, 0xFFE84855.toInt(), true),
            Car("A", 2, 0, 2, false, 0xFF2D9CDB.toInt()),
            Car("B", 3, 1, 2, false, 0xFFF2C14E.toInt()),
            Car("C", 4, 1, 2, false, 0xFF27AE60.toInt()),
            Car("D", 0, 0, 2, true, 0xFF9B59B6.toInt()),
            Car("E", 0, 4, 2, true, 0xFF56CCF2.toInt()),
            Car("F", 2, 4, 2, false, 0xFFF2994A.toInt()),
            Car("G", 3, 5, 2, true, 0xFFEB5757.toInt())
        )),
        Level("Узкий коридор", listOf(
            Car("R", 0, 2, 2, true, 0xFFE84855.toInt(), true),
            Car("A", 2, 0, 2, false, 0xFF2D9CDB.toInt()),
            Car("B", 3, 1, 2, false, 0xFFF2C14E.toInt()),
            Car("C", 4, 1, 2, false, 0xFF27AE60.toInt()),
            Car("D", 0, 0, 2, true, 0xFF9B59B6.toInt()),
            Car("E", 0, 4, 2, true, 0xFF56CCF2.toInt()),
            Car("F", 2, 4, 2, true, 0xFFF2994A.toInt()),
            Car("G", 3, 5, 2, true, 0xFFEB5757.toInt())
        ))
    )

    private var levelIndex = prefs.getInt("level", 0).coerceIn(0, levels.lastIndex)
    private var cars = copyCars(levels[levelIndex].cars)
    private var moves = 0
    private var selected: Car? = null
    private var downX = 0f
    private var downY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSize = 0f
    private var won = false
    private var bestMoves = prefs.getInt("best_$levelIndex", 0)

    private fun copyCars(source: List<Car>) = source.map { it.copy() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bg)
        val w = width.toFloat()
        val h = height.toFloat()
        drawText(canvas, "ПРОБКА", w * .07f, h * .075f, 30f, textColor, true)
        drawText(canvas, "НАЙДИ ВЫХОД", w * .07f, h * .112f, 11f, muted, true)

        val statY = h * .17f
        drawRound(canvas, w * .07f, statY, w * .46f, statY + h * .065f, 14f, panel)
        drawText(canvas, "УРОВЕНЬ ${levelIndex + 1}", w * .10f, statY + h * .043f, 13f, textColor, true)
        drawRound(canvas, w * .54f, statY, w * .93f, statY + h * .065f, 14f, panel)
        drawText(canvas, "ХОДЫ  $moves", w * .58f, statY + h * .043f, 13f, textColor, true)

        boardSize = min(w * .86f, h * .51f)
        cell = boardSize / 6f
        boardLeft = (w - boardSize) / 2f
        boardTop = h * .27f

        drawRound(canvas, boardLeft - 6, boardTop - 6, boardLeft + boardSize + 6,
            boardTop + boardSize + 6, 20f, 0xFF22313D.toInt())
        drawRound(canvas, boardLeft, boardTop, boardLeft + boardSize,
            boardTop + boardSize, 15f, 0xFF17232D.toInt())

        for (r in 0..5) for (c in 0..5) {
            val rect = RectF(boardLeft + c * cell + 3, boardTop + r * cell + 3,
                boardLeft + (c + 1) * cell - 3, boardTop + (r + 1) * cell - 3)
            paint.color = if (r == 2 && c >= 4) 0xFF243A36.toInt() else gridColor
            paint.alpha = 110
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }
        paint.alpha = 255

        val exitY = boardTop + 2 * cell + cell * .22f
        drawRound(canvas, boardLeft + boardSize - 2, exitY, boardLeft + boardSize + 13,
            exitY + cell * .56f, 7f, green)

        cars.forEach { drawCar(canvas, it) }

        val infoY = boardTop + boardSize + h * .055f
        if (won) {
            drawText(canvas, "ВЫЕЗД СВОБОДЕН!", w / 2, infoY, 19f, green, true, true)
            drawText(canvas, "Отлично. Готов к следующему?", w / 2, infoY + 27, 12f, muted, false, true)
        } else {
            drawText(canvas, "Передвигай машины вдоль их оси", w / 2, infoY, 13f, muted, false, true)
            drawText(canvas, "Освободи путь красной машине →", w / 2, infoY + 23, 12f, muted, false, true)
        }

        val btnY = h * .84f
        drawButton(canvas, w * .07f, btnY, w * .45f, btnY + h * .075f, "ЗАНОВО", panel, textColor)
        drawButton(canvas, w * .53f, btnY, w * .93f, btnY + h * .075f,
            if (won) "СЛЕДУЮЩИЙ →" else "ПОДСКАЗКА", if (won) green else 0xFF2D9CDB.toInt(), textColor)
        drawText(canvas, if (bestMoves > 0) "ЛУЧШИЙ РЕЗУЛЬТАТ: $bestMoves ХОДОВ" else "МЕНЬШЕ ХОДОВ — ЛУЧШЕ РЕЗУЛЬТАТ",
            w / 2, h * .955f, 10f, muted, false, true)
    }

    private fun drawCar(canvas: Canvas, car: Car) {
        val inset = cell * .075f
        val left = boardLeft + car.x * cell + inset
        val top = boardTop + car.y * cell + inset
        val right = boardLeft + (car.x + if (car.horizontal) car.length else 1) * cell - inset
        val bottom = boardTop + (car.y + if (car.horizontal) 1 else car.length) * cell - inset
        val rect = RectF(left, top, right, bottom)
        paint.color = 0x44000000
        canvas.drawRoundRect(RectF(rect.left + 1, rect.top + 4, rect.right + 1, rect.bottom + 4), 13f, 13f, paint)
        paint.color = car.color
        canvas.drawRoundRect(rect, 13f, 13f, paint)

        val shortSide = min(rect.width(), rect.height())
        paint.color = 0x33FFFFFF
        if (car.horizontal) {
            val glass = RectF(rect.left + rect.width() * .28f, rect.top + shortSide * .19f,
                rect.right - rect.width() * .28f, rect.bottom - shortSide * .19f)
            canvas.drawRoundRect(glass, 7f, 7f, paint)
            paint.color = 0x55000000
            canvas.drawRoundRect(RectF(rect.left + 5, rect.top + 3, rect.left + 10, rect.bottom - 3), 3f, 3f, paint)
            canvas.drawRoundRect(RectF(rect.right - 10, rect.top + 3, rect.right - 5, rect.bottom - 3), 3f, 3f, paint)
        } else {
            val glass = RectF(rect.left + shortSide * .19f, rect.top + rect.height() * .28f,
                rect.right - shortSide * .19f, rect.bottom - rect.height() * .28f)
            canvas.drawRoundRect(glass, 7f, 7f, paint)
            paint.color = 0x55000000
            canvas.drawRoundRect(RectF(rect.left + 3, rect.top + 5, rect.right - 3, rect.top + 10), 3f, 3f, paint)
            canvas.drawRoundRect(RectF(rect.left + 3, rect.bottom - 10, rect.right - 3, rect.bottom - 5), 3f, 3f, paint)
        }
        if (car.target) {
            paint.color = 0xFFFFD6D9.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRoundRect(RectF(rect.left + 2, rect.top + 2, rect.right - 2, rect.bottom - 2), 11f, 11f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawButton(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, color: Int, fg: Int) {
        drawRound(canvas, l, t, r, b, 16f, color)
        drawText(canvas, label, (l + r) / 2, (t + b) / 2 + 5, 12f, fg, true, true)
    }

    private fun drawRound(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, color: Int) {
        paint.color = color
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, paint)
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, size: Float,
                         color: Int, bold: Boolean = false, center: Boolean = false) {
        paint.color = color
        paint.textSize = size * resources.displayMetrics.scaledDensity
        paint.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        canvas.drawText(text, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                if (downY in boardTop..(boardTop + boardSize)) {
                    selected = cars.lastOrNull { car ->
                        val l = boardLeft + car.x * cell
                        val t = boardTop + car.y * cell
                        val r = boardLeft + (car.x + if (car.horizontal) car.length else 1) * cell
                        val b = boardTop + (car.y + if (car.horizontal) 1 else car.length) * cell
                        downX in l..r && downY in t..b
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val car = selected ?: return true
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                val delta = if (car.horizontal) dx else dy
                if (abs(delta) >= cell * .36f) {
                    val steps = (delta / cell).toInt().let { if (it == 0) if (delta > 0) 1 else -1 else it }
                    moveCar(car, steps)
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (selected == null && downY > height * .82f) {
                    if (downX < width * .49f) resetLevel()
                    else if (won) nextLevel() else showHint()
                }
                selected = null
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun moveCar(car: Car, requested: Int) {
        if (won) return
        val sign = if (requested > 0) 1 else -1
        repeat(abs(requested).coerceAtMost(6)) {
            val nx = car.x + if (car.horizontal) sign else 0
            val ny = car.y + if (!car.horizontal) sign else 0
            if (car.target && nx + car.length > 6) {
                car.x = 6 - car.length
                moves++
                won = true
                if (bestMoves == 0 || moves < bestMoves) {
                    bestMoves = moves
                    prefs.edit().putInt("best_$levelIndex", bestMoves).apply()
                }
                return
            }
            if (nx < 0 || ny < 0 ||
                nx + (if (car.horizontal) car.length else 1) > 6 ||
                ny + (if (car.horizontal) 1 else car.length) > 6) return
            val blocked = cars.any { other ->
                if (other === car) false else {
                    val oxEnd = other.x + if (other.horizontal) other.length else 1
                    val oyEnd = other.y + if (other.horizontal) 1 else other.length
                    val cxEnd = nx + if (car.horizontal) car.length else 1
                    val cyEnd = ny + if (car.horizontal) 1 else car.length
                    nx < oxEnd && cxEnd > other.x && ny < oyEnd && cyEnd > other.y
                }
            }
            if (!blocked) {
                car.x = nx
                car.y = ny
                moves++
            } else return
        }
    }

    private fun resetLevel() {
        cars = copyCars(levels[levelIndex].cars)
        moves = 0
        won = false
        invalidate()
    }

    private fun nextLevel() {
        levelIndex = (levelIndex + 1) % levels.size
        prefs.edit().putInt("level", levelIndex).apply()
        bestMoves = prefs.getInt("best_$levelIndex", 0)
        resetLevel()
    }

    private fun showHint() {
        selected = cars.firstOrNull { it.target }
        invalidate()
    }
}