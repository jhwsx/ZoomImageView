package com.example.zoomimageview

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.*
import androidx.appcompat.widget.AppCompatImageView
import androidx.viewpager.widget.ViewPager
import kotlin.math.sqrt

/**
 * 功能点：
 * 1， 图片居中显示，初始化缩放为屏幕同宽
 * onAttachedToWindow 中，注册 viewTreeObserver.addOnGlobalLayoutListener 监听回调
 * onDetachedFromWindow 中，注销 viewTreeObserver.removeOnGlobalLayoutListener 监听回调
 * 在 onGlobalLayout 回调方法里，获取控件宽高，计算初始化缩放比例，最小缩放比例，中等缩放比例，最大缩放比例
 * 使用 Matrix， setImageMatrix 先使图片居中，再进行缩放。
 * 2， 手势实现居中缩放
 * 使用 ScaleGestureDetector，在 onScale 方法里处理，注意 onScaleBegin 需要返回 true；
 * 设置 setOnTouchListener，在 onTouchEvent 回调中，把触摸事件交给 ScaleGestureDetector 对象处理；
 * 在 onScale 方法中，获取当前的 scale 比例，以及缩放手势的 scaleFactor，计算新的缩放因子，设置给 setImageMatrix。
 * 3， 设置图片的缩放中心为手势的中心：detector.getFocusX(), detector.getFocusY()
 * 处理当图片当前宽度小于控件宽度时不居中的问题，处理当图片当前宽度大于控件宽度时的左边或右边出现白边的问题；高度上也一样的问题。
 * 4， 图片的自由移动
 * 这里需要做好移动的判断：当前手势中心与上次手势中心的 x 向的差值和 y 向的差值的平方和的平方根是否大于最小滑动距离。
 * 移动时进行边界检查：只检查图片宽度大于控件宽度或者图片高度大于控件高度，避免出现移动导致的白边。
 * 5， 双击时缩放
 * 使用 GestureDetector，把触摸事件传递给 GestureDetector 的 onTouchEvent 方法中
 * 覆盖 onDoubleTap 方法，当双击时会回调到这里。
 * 使用 postDelayed 分段来缩放。
 * 6， 处理与 ViewPager 结合的滑动冲突
 *
 * @author wangzhichao
 * @since 20-3-5
 */

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), ViewTreeObserver.OnGlobalLayoutListener,
    ScaleGestureDetector.OnScaleGestureListener, View.OnTouchListener {
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0
    private var isHasInit: Boolean = false
    private var initScale: Float = 0f
    private var minScale: Float = 0f
    private var midScale: Float = 0f
    private var maxScale: Float = 0f
    private val picMatrix = Matrix()
    private val scaleGestureDetector = ScaleGestureDetector(context, this)
    //------------------move
    private var isCanDrag: Boolean = false
    private var lastScaleX: Float = 0f
    private var lastScaleY: Float = 0f
    private var lastPointerCount: Int = 0
    private var isCheckLeftAndRight = false
    private var isCheckTopAndBottom = false
    private val scaledTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    //-----------------双击缩放
    private var isAutoScaling: Boolean = false
    private val simpleGestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            Log.d("gesture", "onDoubleTap")
            if (isAutoScaling) {
                return true;
            }
            val x = e.x
            val y = e.y

            val currentScale = getScale()
            val targetScale = when {
                currentScale >= minScale && currentScale < midScale -> {
                    midScale
                }
                currentScale >= midScale && currentScale < maxScale -> {
                    maxScale
                }
                else -> {
                    initScale
                }
            }
            postDelayed(AutoScaleRunnable(targetScale, x, y), 0)
            return true
        }
    }
    private val gestureDetector = GestureDetector(context, simpleGestureListener)

    init {
        scaleType = ScaleType.MATRIX
        setOnTouchListener(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalLayoutListener(this)
    }

    override fun onGlobalLayout() {
        // 获取控件的宽高
        viewWidth = width
        viewHeight = height
        Log.d(TAG, "viewWidth = $viewWidth, viewHeight = $viewHeight")
        // 添加初始化的标记，因为 onGlobalLayout() 方法会多次回调
        if (!isHasInit) {
            if (drawable == null) {
                return
            }
            // 获取图片实际的宽高
            val picWidth = drawable.intrinsicWidth
            val picHeight = drawable.intrinsicHeight
            Log.d(TAG, "picWidth = $picWidth, picHeight = $picHeight")
            // 计算 scale
            initScale = viewWidth * 1f / picWidth
            minScale = initScale * 0.8f
            midScale = initScale * 2f
            maxScale = initScale * 4f
            // 先把图片移动到控件的中心
            val dx = viewWidth * 1f / 2 - picWidth * 1f / 2
            val dy = viewHeight * 1f / 2 - picHeight * 1f / 2
            picMatrix.postTranslate(dx, dy)
            picMatrix.postScale(initScale, initScale, viewWidth * 1f / 2, viewHeight * 1f / 2)
            imageMatrix = picMatrix
            // 初始化完毕，修改标记为 true
            isHasInit = true
        }
    }

    override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
        Log.d(TAG, "onScaleBegin")
        // 这里需要返回 true,表示开始监听
        return true
    }

    override fun onScaleEnd(detector: ScaleGestureDetector) {
        // do nothing
        Log.d(TAG, "onScaleEnd")
    }

    override fun onScale(detector: ScaleGestureDetector): Boolean {
        Log.d(TAG, "onScale")
        val currentScale = getScale()
        val scaleFactor = detector.scaleFactor
        if (drawable == null) {
            return true
        }
        Log.d(TAG, "currentScale=$currentScale, maxScale=$maxScale, minScale=$minScale")
        // 对新的 scale 进行边界控制，不能大于 maxScale，不能小于 minScale
        val newScale = currentScale * scaleFactor
        val newScaleFactor = when {
            newScale > maxScale -> {
                maxScale * 1f / currentScale
            }
            newScale < minScale -> {
                minScale * 1f / currentScale
            }
            else -> {
                scaleFactor
            }
        }
        Log.d(
            TAG,
            "currentScale = $currentScale, scaleFactor=$scaleFactor, maxScale=$maxScale, minScale=$minScale, newScaleFactor=$newScaleFactor"
        )
//        picMatrix.postScale(newScaleFactor, newScaleFactor, viewWidth * 0.5f, viewHeight * 0.5f)
        // 图片的缩放中心改为手势的中心
        picMatrix.postScale(newScaleFactor, newScaleFactor, detector.focusX, detector.focusY)
        Log.d(TAG, "getMatrixRectF() = ${getMatrixRectF().toString()}")
        checkBorderAndCenterWhenScale()
        imageMatrix = picMatrix
        return true
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            return true
        }
        if (event.pointerCount > 1) {
            // 是多指操作， 把触摸事件传递给 scaleGestureDetector
            scaleGestureDetector.onTouchEvent(event)
        }
        handleDrag(event)
        return true
    }

    private fun handleDrag(event: MotionEvent) {
        // 计算触控中心
        val pointerCount = event.pointerCount
        val list: MutableList<Pair<Float, Float>> = mutableListOf()
        for (i in 0 until pointerCount) {
            list.add(i, Pair(event.getX(i), event.getY(i)))
        }
        val scaleX = (list.sumByDouble { it.first.toDouble() } / list.size).toFloat()
        val scaleY = (list.sumByDouble { it.second.toDouble() } / list.size).toFloat()

        if (lastPointerCount != pointerCount) {
            // 本次触控点数量和上一次的触控点数量不一致
            lastScaleX = scaleX
            lastScaleY = scaleY
            isCanDrag = false
        }
        lastPointerCount = pointerCount

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val matrixRectF = getMatrixRectF()
                // 处理与 ViewPager 结合的滑动冲突
                if (matrixRectF.width() > viewWidth + 0.01f) {
                    if (parent is ViewPager) {
                        parent.requestDisallowInterceptTouchEvent(true)
                        if (matrixRectF.right.toInt() == viewWidth
                            || matrixRectF.left.toInt() == 0) {
                            parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                }

                var dx = scaleX - lastScaleX
                var dy = scaleY - lastScaleY
                Log.d("wzc", "dx = $dx, dy = $dy")
                if (!isCanDrag) {
                    isCanDrag = isMoveAction(dx, dy)
                }
                if (isCanDrag) {

                    Log.d("wzc", "isCanDrag = $isCanDrag")
                    if (drawable != null) {
                        isCheckLeftAndRight = true
                        isCheckTopAndBottom = true

                        if (matrixRectF.width() < viewWidth) {
                            // 图片的当前宽度小于控件的宽度
                            isCheckLeftAndRight = false
                            dx = 0F
                        }
                        if (matrixRectF.height() < viewHeight) {
                            // 图片的当前高度小于控件的高度
                            isCheckTopAndBottom = false
                            dy = 0F
                        }
                    }
                    picMatrix.postTranslate(dx, dy)
                    checkBorderWhenTranslate()
                    imageMatrix = picMatrix
                }
                lastScaleX = scaleX
                lastScaleY = scaleY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastPointerCount = 0
            }
            else -> {
            }
        }
    }

    private fun isMoveAction(dx: Float, dy: Float): Boolean {
        return sqrt((dx * dx + dy * dy).toDouble()) > scaledTouchSlop
    }

    /**
     * 防止在以触摸点为缩放中心时，导致的图片和控件之间出现空白的边
     */
    private fun checkBorderAndCenterWhenScale() {
        val matrixRectF = getMatrixRectF()
        // 图片当前的宽高
        val currPicWidth = matrixRectF.width()
        val currPicHeight = matrixRectF.height()
        var dx = 0F
        var dy = 0F
        // 当前图片的宽度大于控件的宽度
        if (currPicWidth >= viewWidth) {
            if (matrixRectF.left > 0) {
                // 左边有空白，就向左偏移
                dx = -matrixRectF.left
            }
            if (matrixRectF.right < viewWidth) {
                // 右边有空白，就向右偏移
                dx = viewWidth - matrixRectF.right
            }
        } else {
            // 当前图片的宽度小于控件的宽度
            dx = viewWidth * 0.5f - matrixRectF.right + currPicWidth * 0.5f
        }
        if (currPicHeight >= viewHeight) {
            if (matrixRectF.top > 0) {
                // 顶部有空白，就向顶部偏移
                dy = -matrixRectF.top
            }
            if (matrixRectF.bottom < viewHeight) {
                dy = viewHeight - matrixRectF.bottom
            }
        } else {
            dy = viewHeight * 0.5f - matrixRectF.bottom + currPicHeight * 0.5f
        }
        picMatrix.postTranslate(dx, dy)
    }

    /**
     * 在拖动图片时进行边界检查, 避免出现白边
     */
    private fun checkBorderWhenTranslate() {
        val matrixRectF = getMatrixRectF()
        var dx = 0F
        var dy = 0F

        if (matrixRectF.top > 0 && isCheckTopAndBottom) {
            dy = -matrixRectF.top
        }
        if (matrixRectF.bottom < viewHeight && isCheckTopAndBottom) {
            dy = viewHeight - matrixRectF.bottom
        }
        if (matrixRectF.left > 0 && isCheckLeftAndRight) {
            dx = -matrixRectF.left
        }
        if (matrixRectF.right < viewWidth && isCheckLeftAndRight) {
            dx = viewWidth - matrixRectF.right
        }
        picMatrix.postTranslate(dx, dy)
        imageMatrix = picMatrix
    }

    private fun getScale(): Float {
        val array = Array(9) { 0F }.toFloatArray()
        picMatrix.getValues(array)
        return array[Matrix.MSCALE_X]
    }


    /**
     * 获取图片缩放后所在的 RectF（包含大小和位置）
     * 这里返回的图片在屏幕上真正的坐标以及大小
     */
    private fun getMatrixRectF(): RectF {
        val result = RectF()
        val matrix = picMatrix
        if (drawable != null) {
            // 这里需要把图片的固有尺寸设置给 RectF，然后调用 Matrix 的 mapRect 方法，这样才可以得到变换后的
            result.set(
                0f, 0f,
                drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat()
            )
            matrix.mapRect(result)
        }
        return result
    }

    private inner class AutoScaleRunnable(
        private val targetScale: Float,
        private val x: Float,
        private val y: Float
    ) : Runnable {
        private val scaleValue = if (getScale() < targetScale) LARGER else SMALLER
        override fun run() {
            isAutoScaling = true
            picMatrix.postScale(scaleValue, scaleValue, x, y)
            checkBorderAndCenterWhenScale()
            imageMatrix = picMatrix

            // 判断是否已经达到目标大小
            val currentScale = getScale()
            // 正在放大且当前比目标还小，或者，正在缩小且当前比目标还大
            if (scaleValue > 1.0f && targetScale > currentScale
                || scaleValue < 1.0f && targetScale < currentScale
            ) {
                postDelayed(this, 0)
            } else {
                val finalScale = targetScale * 1f / currentScale
                picMatrix.postScale(finalScale, finalScale, x, y)
                checkBorderAndCenterWhenScale()
                imageMatrix = picMatrix
                isAutoScaling = false
            }
        }


    }

    companion object {
        private const val LARGER = 1.1f
        private const val SMALLER = 0.9f
        private const val TAG = "ZoomImageView"
    }


}