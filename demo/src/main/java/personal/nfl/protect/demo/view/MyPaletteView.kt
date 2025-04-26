package personal.nfl.protect.demo.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class MyPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var paint: Paint = Paint()
    private var touchAction = MotionEvent.ACTION_CANCEL
    private val pointList = ArrayList<PointF>()
    private var lastPointF = PointF()
    private val path = Path()
    private val drawPath = Path()
    private var lastMoveTime = 0L
    private var startX = 0f
    private var startY = 0f
    private var startOffsetX = 0f
    private var startOffsetY = 0f
    private var hasDraw = false
    private val nextPointDuring = 100
    private val density = 3
    private val minAvailableSize = 20 * density
    private var noMoveLineTo = true

    init {
        paint.color = Color.RED
        paint.strokeWidth = 15f
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        this.isClickable = true

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
        if (hasDraw) {
            canvas.drawPath(path, paint)
        } else {
            drawPath.reset()
            drawPath.addPath(path)
            if (lastPointF.x >= 0) {
                drawPath.lineTo(lastPointF.x, lastPointF.y)
            }
            canvas.drawPath(drawPath, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (hasDraw) {
            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    startOffsetX = this.translationX
                    startOffsetY = this.translationY
                }
                MotionEvent.ACTION_MOVE -> {
                    this.translationX = startOffsetX + event.rawX - startX
                    this.translationY = startOffsetY + event.rawY - startY
                }
            }
            return super.onTouchEvent(event)
        }
        touchAction = event.action
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                lastPointF.x = event.x
                lastPointF.y = event.y
                path.moveTo(startX, startY)
                lastMoveTime = System.currentTimeMillis()
            }

            MotionEvent.ACTION_MOVE -> {
                System.currentTimeMillis().let {
                    if (it - lastMoveTime > nextPointDuring) {
                        noMoveLineTo = false
                        path.lineTo(event.x, event.y)
                        lastPointF.x = -1f
                        lastPointF.y = -1f
                    } else {
                        lastPointF.x = event.x
                        lastPointF.y = event.y
                    }
                    lastMoveTime = it
                }
            }

            MotionEvent.ACTION_UP -> {
                hasDraw = true
                System.currentTimeMillis().let {
                    if (it - lastMoveTime <= nextPointDuring) {
                        if (Math.abs(event.x - startX) > minAvailableSize) {
                            if (lastPointF.x >= 0) {
                                path.lineTo(lastPointF.x, lastPointF.y)
                            } else {
                                path.lineTo(event.x, event.y)
                            }
                        }
                    }
                }
                lastPointF.x = -1f
                lastPointF.y = -1f
                if (noMoveLineTo) {
                    path.lineTo(event.x, event.y)
                }
                path.close()
            }
        }
        invalidate()
        return super.onTouchEvent(event)
    }

    fun clear() {
        hasDraw = false
        path.reset()
        drawPath.reset()
        lastPointF.x = -1f
        lastPointF.y = -1f
        this.translationX = 0f
        this.translationY = 0f
        invalidate()
    }
}