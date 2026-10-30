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
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(16, 24, 32)
        window.navigationBarColor = android.graphics.Color.rgb(16, 24, 32)
        setContentView(ProbkaView(this))
    }
}

private data class Car(
    val id: String, var x: Int, var y: Int, val length: Int,
    val horizontal: Boolean, val color: Int, val target: Boolean = false
)

private data class Level(val cars: List<Car>, val scramble: Int)

class ProbkaView(context: Context) : View(context) {
    private val bg = 0xFF101820.toInt()
    private val panel = 0xFF1B2732.toInt()
    private val gridColor = 0xFF31414E.toInt()
    private val textColor = 0xFFF4F7FA.toInt()
    private val muted = 0xFF9EADB9.toInt()
    private val accent = 0xFFE84855.toInt()
    private val green = 0xFF27AE60.toInt()
    private val blue = 0xFF2D9CDB.toInt()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val prefs = context.getSharedPreferences("probka_progress", Context.MODE_PRIVATE)
    private val levels = LevelFactory.create(100)
    private var levelIndex = prefs.getInt("level", 0).coerceIn(0, 99)
    private var unlocked = prefs.getInt("unlocked", 0).coerceIn(0, 99)
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
    private var levelPicker = false
    private var pickerScroll = 0
    private var hintIds = emptySet<String>()
    private var bestMoves = prefs.getInt("best_0", 0)
    private var message = ""

    private fun copyCars(source: List<Car>) = source.map { it.copy() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bg)
        if (levelPicker) {
            drawLevelPicker(canvas)
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        drawText(canvas, "ПРОБКА", w * .07f, h * .065f, 29f, textColor, true)
        drawText(canvas, "НАЙДИ ВЫХОД", w * .07f, h * .098f, 11f, muted, true)

        val statY = h * .135f
        drawRound(canvas, w * .07f, statY, w * .48f, statY + h * .052f, 13f, panel)
        drawText(canvas, "УРОВЕНЬ " + (levelIndex + 1) + "  ▾", w * .10f, statY + h * .035f, 12f, textColor, true)
        drawRound(canvas, w * .54f, statY, w * .93f, statY + h * .052f, 13f, panel)
        drawText(canvas, "ХОДЫ  " + moves, w * .58f, statY + h * .035f, 12f, textColor, true)

        boardSize = min(w * .86f, h * .49f)
        cell = boardSize / 6f
        boardLeft = (w - boardSize) / 2f
        boardTop = h * .215f

        drawRound(canvas, boardLeft - 6, boardTop - 6, boardLeft + boardSize + 6,
            boardTop + boardSize + 6, 19f, 0xFF22313D.toInt())
        drawRound(canvas, boardLeft, boardTop, boardLeft + boardSize,
            boardTop + boardSize, 14f, 0xFF17232D.toInt())

        for (r in 0..5) for (c in 0..5) {
            val rect = RectF(boardLeft + c * cell + 3, boardTop + r * cell + 3,
                boardLeft + (c + 1) * cell - 3, boardTop + (r + 1) * cell - 3)
            paint.color = if (r == 2 && c >= 4) 0xFF243A36.toInt() else gridColor
            paint.alpha = 110
            canvas.drawRoundRect(rect, 8f, 8f, paint)
        }
        paint.alpha = 255
        val exitY = boardTop + 2 * cell + cell * .22f
        drawRound(canvas, boardLeft + boardSize - 2, exitY, boardLeft + boardSize + 12,
            exitY + cell * .56f, 6f, green)
        cars.forEach { drawCar(canvas, it) }

        val infoY = boardTop + boardSize + h * .045f
        if (won) {
            drawText(canvas, "ВЫЕЗД СВОБОДЕН!", w / 2, infoY, 17f, green, true, true)
            drawText(canvas, "Результат: " + moves + " ходов  •  " + starsFor(moves) + " ★", w / 2, infoY + h * .027f, 12f, muted, false, true)
        } else {
            drawText(canvas, message.ifEmpty { "Передвигай машины вдоль их оси" }, w / 2, infoY, 12f, muted, false, true)
            drawText(canvas, "Освободи путь красной машине →", w / 2, infoY + h * .026f, 11f, muted, false, true)
        }

        val btnY = h * .835f
        drawButton(canvas, w * .07f, btnY, w * .45f, btnY + h * .068f, "ЗАНОВО", panel, textColor)
        drawButton(canvas, w * .53f, btnY, w * .93f, btnY + h * .068f,
            if (won) "СЛЕДУЮЩИЙ →" else "ПОДСКАЗКА", if (won) green else blue, textColor)
        drawText(canvas, "ЛУЧШИЙ РЕЗУЛЬТАТ: " + if (bestMoves > 0) bestMoves.toString() + " ХОДОВ" else "—",
            w / 2, h * .93f, 10f, muted, false, true)
        drawText(canvas, "ПРОЙДЕНО: " + (unlocked + 1) + " / 100", w / 2, h * .958f, 9f, muted, false, true)
    }

    private fun drawLevelPicker(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawText(canvas, "ВЫБОР УРОВНЯ", w * .07f, h * .07f, 23f, textColor, true)
        drawText(canvas, "Открыто: " + (unlocked + 1) + " из 100", w * .07f, h * .105f, 12f, muted)
        val cols = 5
        val gap = w * .025f
        val left = w * .07f
        val top = h * .15f
        val itemW = (w * .86f - gap * (cols - 1)) / cols
        val itemH = min(itemW * .88f, h * .075f)
        val rowGap = h * .014f
        for (i in 0 until 100) {
            val row = i / cols - pickerScroll
            if (row < 0 || row > 8) continue
            val col = i % cols
            val x = left + col * (itemW + gap)
            val y = top + row * (itemH + rowGap)
            val isOpen = i <= unlocked
            val color = if (i == levelIndex) blue else if (isOpen) panel else 0xFF141E27.toInt()
            drawRound(canvas, x, y, x + itemW, y + itemH, 10f, color)
            drawText(canvas, if (isOpen) (i + 1).toString() else "🔒",
                x + itemW / 2, y + itemH * .58f, 14f, if (isOpen) textColor else muted, true, true)
            val best = prefs.getInt("best_" + i, 0)
            if (best > 0) drawText(canvas, "★", x + itemW / 2, y + itemH * .88f, 9f, 0xFFF2C14E.toInt(), true, true)
        }
        drawText(canvas, "ПРОКРУТИ СПИСОК  •  НАЖМИ НА УРОВЕНЬ", w / 2, h * .91f, 10f, muted, false, true)
        drawButton(canvas, w * .25f, h * .94f, w * .75f, h * .99f, "НАЗАД", panel, textColor)
    }

    private fun drawCar(canvas: Canvas, car: Car) {
        val inset = cell * .075f
        val left = boardLeft + car.x * cell + inset
        val top = boardTop + car.y * cell + inset
        val right = boardLeft + (car.x + if (car.horizontal) car.length else 1) * cell - inset
        val bottom = boardTop + (car.y + if (car.horizontal) 1 else car.length) * cell - inset
        val rect = RectF(left, top, right, bottom)
        paint.color = 0x44000000
        canvas.drawRoundRect(RectF(rect.left + 1, rect.top + 4, rect.right + 1, rect.bottom + 4), 12f, 12f, paint)
        paint.color = car.color
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        val shortSide = min(rect.width(), rect.height())
        paint.color = 0x33FFFFFF
        if (car.horizontal) {
            canvas.drawRoundRect(RectF(rect.left + rect.width() * .28f, rect.top + shortSide * .19f,
                rect.right - rect.width() * .28f, rect.bottom - shortSide * .19f), 7f, 7f, paint)
        } else {
            canvas.drawRoundRect(RectF(rect.left + shortSide * .19f, rect.top + rect.height() * .28f,
                rect.right - shortSide * .19f, rect.bottom - rect.height() * .28f), 7f, 7f, paint)
        }
        if (car.target || car.id in hintIds) {
            paint.color = if (car.target) 0xFFFFD6D9.toInt() else 0xFFFFE08A.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            canvas.drawRoundRect(RectF(rect.left + 2, rect.top + 2, rect.right - 2, rect.bottom - 2), 10f, 10f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawButton(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, label: String, color: Int, fg: Int) {
        drawRound(canvas, l, t, r, b, 14f, color)
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
                downX = event.x; downY = event.y
                lastTouchX = event.x; lastTouchY = event.y
                if (!levelPicker && downY in boardTop..(boardTop + boardSize)) {
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
                if (levelPicker) {
                    val dy = event.y - lastTouchY
                    if (abs(dy) > 12f) {
                        pickerScroll = (pickerScroll + if (dy < 0) 1 else -1).coerceIn(0, 12)
                        lastTouchY = event.y
                        invalidate()
                    }
                    return true
                }
                val car = selected ?: return true
                val delta = if (car.horizontal) event.x - lastTouchX else event.y - lastTouchY
                if (abs(delta) >= cell * .42f) {
                    val steps = (delta / cell).toInt().let { if (it == 0) if (delta > 0) 1 else -1 else it }
                    moveCar(car, steps)
                    lastTouchX = event.x; lastTouchY = event.y
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (levelPicker) {
                    if (downY > height * .93f) {
                        levelPicker = false
                    } else if (abs(event.y - downY) < 20f) {
                        val w = width.toFloat(); val h = height.toFloat()
                        val cols = 5; val gap = w * .025f; val left = w * .07f; val top = h * .15f
                        val itemW = (w * .86f - gap * (cols - 1)) / cols
                        val itemH = min(itemW * .88f, h * .075f); val rowGap = h * .014f
                        val col = ((event.x - left) / (itemW + gap)).toInt()
                        val row = ((event.y - top) / (itemH + rowGap)).toInt() + pickerScroll
                        if (col in 0 until cols && row in 0..19) {
                            val chosen = row * cols + col
                            if (chosen <= unlocked && chosen < 100) loadLevel(chosen)
                        }
                    }
                    invalidate()
                    return true
                }
                if (selected == null) {
                    val h = height.toFloat()
                    if (downY in (h * .12f)..(h * .20f)) {
                        levelPicker = true
                    } else if (downY > h * .82f) {
                        if (downX < width * .49f) resetLevel()
                        else if (won) nextLevel() else showHint()
                    }
                }
                selected = null
                performClick()
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

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
                unlocked = maxOf(unlocked, levelIndex + 1).coerceAtMost(99)
                prefs.edit().putInt("unlocked", unlocked).putInt("level", levelIndex).apply()
                val oldBest = prefs.getInt("best_" + levelIndex, 0)
                if (oldBest == 0 || moves < oldBest) {
                    bestMoves = moves
                    prefs.edit().putInt("best_" + levelIndex, bestMoves).apply()
                } else bestMoves = oldBest
                message = ""
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
                car.x = nx; car.y = ny; moves++
                hintIds = emptySet()
            } else return
        }
    }

    private fun resetLevel() {
        cars = copyCars(levels[levelIndex].cars)
        moves = 0; won = false; hintIds = emptySet(); message = ""
        invalidate()
    }

    private fun loadLevel(index: Int) {
        levelIndex = index.coerceIn(0, unlocked)
        prefs.edit().putInt("level", levelIndex).apply()
        bestMoves = prefs.getInt("best_" + levelIndex, 0)
        levelPicker = false
        resetLevel()
    }

    private fun nextLevel() {
        if (levelIndex < 99 && levelIndex + 1 <= unlocked) loadLevel(levelIndex + 1)
        else {
            levelPicker = true
            message = "Все доступные уровни пройдены!"
        }
    }

    private fun showHint() {
        val target = cars.first { it.target }
        val blockers = cars.filter { !it.horizontal && it.x in (target.x + target.length)..5 && target.y in it.y until (it.y + it.length) }
        hintIds = blockers.map { it.id }.toSet()
        message = if (blockers.isEmpty()) "Красная машина может выезжать →" else "Подсвечены машины, мешающие выезду"
        invalidate()
    }

    private fun starsFor(count: Int): Int {
        val threshold = (17 - levelIndex / 12).coerceAtLeast(8)
        return when {
            count <= threshold -> 3
            count <= threshold + 7 -> 2
            else -> 1
        }
    }
}

private object LevelFactory {
    private val palette = intArrayOf(
        0xFFE84855.toInt(), 0xFF2D9CDB.toInt(), 0xFFF2C14E.toInt(),
        0xFF27AE60.toInt(), 0xFF9B59B6.toInt(), 0xFF56CCF2.toInt(),
        0xFFF2994A.toInt(), 0xFFEB5757.toInt()
    )

    fun create(count: Int): List<Level> {
        val result = ArrayList<Level>(count)
        for (level in 0 until count) {
            val cars = base(level % 3)
            val random = Random(8173 + level * 7919)
            val wanted = 12 + level * 2
            var done = 0
            var attempts = 0
            while (done < wanted && attempts < wanted * 80) {
                attempts++
                val car = cars[random.nextInt(cars.size)]
                if (car.target) continue
                val direction = if (random.nextBoolean()) 1 else -1
                val nx = car.x + if (car.horizontal) direction else 0
                val ny = car.y + if (!car.horizontal) direction else 0
                if (nx < 0 || ny < 0 ||
                    nx + (if (car.horizontal) car.length else 1) > 6 ||
                    ny + (if (car.horizontal) 1 else car.length) > 6) continue
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
                    car.x = nx; car.y = ny; done++
                }
            }
            result.add(Level(cars.map { it.copy() }, done))
        }
        return result
    }

    private fun base(targetStart: Int): MutableList<Car> = mutableListOf(
        Car("R", targetStart, 2, 2, true, palette[0], true),
        Car("A", 0, 0, 2, false, palette[1]),
        Car("B", 1, 0, 3, false, palette[2]),
        Car("C", 2, 0, 2, false, palette[3]),
        Car("D", 3, 0, 2, false, palette[4]),
        Car("E", 0, 3, 2, true, palette[5]),
        Car("F", 2, 3, 2, true, palette[6]),
        Car("G", 4, 3, 2, false, palette[7]),
        Car("H", 4, 1, 2, false, 0xFFB0BEC5.toInt())
    )
}