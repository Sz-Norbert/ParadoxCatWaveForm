package com.paradoxcat.waveformtest.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.nio.ByteBuffer
import kotlin.math.pow

class WaveformSlideBar(context: Context, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        const val LEFT_RIGHT_PADDING = 50.0f
        const val TOP_BOTTOM_PADDING = 50.0f
        private val MAX_VALUE = 2.0f.pow(16.0f) - 1
        val INV_MAX_VALUE = 1.0f / MAX_VALUE

        fun transformRawData(buffer: ByteBuffer, width: Int): Array<Pair<Int, Int>> {
            val result = Array(width) { Pair(0, 0) }
            val totalSamples = buffer.limit() / 2
            val samplesPerPixel = totalSamples.toFloat() / width

            for (pixelIndex in 0 until width) {
                val sampleRange = calculateSampleRange(pixelIndex, samplesPerPixel, totalSamples)
                val samples = extractSamples(buffer, sampleRange)
                result[pixelIndex] = findMinMaxAmplitude(samples)
            }
            return result
        }

        private fun calculateSampleRange(
            pixelIndex: Int,
            samplesPerPixel: Float,
            totalSamples: Int
        ): IntRange {
            val startSample = (pixelIndex * samplesPerPixel).toInt()
            val endSample = ((pixelIndex + 1) * samplesPerPixel).toInt().coerceAtMost(totalSamples)
            return startSample until endSample
        }

        private fun extractSamples(buffer: ByteBuffer, sampleRange: IntRange): List<Int> {
            val samples = mutableListOf<Int>()
            for (sampleIndex in sampleRange) {
                val sample = readSampleFromBuffer(buffer, sampleIndex)
                sample?.let { samples.add(it) }
            }
            return samples
        }


        private fun readSampleFromBuffer(buffer: ByteBuffer, sampleIndex: Int): Int? {
            val firstBytePosition = sampleIndex * 2
            val secondBytePosition = firstBytePosition + 1
            
            if (secondBytePosition >= buffer.limit()) {
                return null
            }
            
            val firstByteValue = buffer[firstBytePosition].toInt()
            val secondByteValue = buffer[secondBytePosition].toInt()
            
            val shiftedSecondByte = secondByteValue * 256
            val combinedValue = shiftedSecondByte + firstByteValue
            
            return combinedValue
        }

        private fun findMinMaxAmplitude(samples: List<Int>): Pair<Int, Int> {
            val min = samples.minOrNull() ?: 0
            val max = samples.maxOrNull() ?: 0
            return Pair(min, max)
        }
    }

    private val linePaint = Paint()
    private val markerPaint = Paint()

    private lateinit var waveForm: Array<Pair<Int, Int>>
    private var originalBuffer: ByteBuffer? = null
    private var progressFraction: Float = 0f

    private var zoomFactor: Float = 1f
    private val maxZoom = 16f

    private val scaleDetector =
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newZoom = (zoomFactor * detector.scaleFactor).coerceIn(1f, maxZoom)
                if (newZoom != zoomFactor) {
                    setZoom(newZoom)
                }
                return true
            }
        })

    init {
        linePaint.color = Color.rgb(0, 255, 0)
        linePaint.isAntiAlias = true

        markerPaint.color = Color.rgb(255, 64, 64)
        markerPaint.strokeWidth = 3f
        markerPaint.isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (::waveForm.isInitialized) {
            drawWaveform(canvas)
            drawProgressMarker(canvas)
        }
    }

    private fun drawWaveform(canvas: Canvas) {
        val drawableW = getDrawableWidth()
        val centerY = height / 2.0f
        val amplitudeScaleFactor = getAmplitudeScaleFactor()

        val totalWidth = drawableW * zoomFactor
        val pixelWidth = totalWidth / waveForm.size
        val startX = (width - totalWidth) / 2

        setLineStrokeWidth(pixelWidth)

        for (i in waveForm.indices) {
            val x = startX + (i * pixelWidth)

            if (x >= -pixelWidth && x <= width + pixelWidth) {
                val yMin = centerY - waveForm[i].first * amplitudeScaleFactor
                val yMax = centerY - waveForm[i].second * amplitudeScaleFactor
                canvas.drawLine(x, yMax, x, yMin, linePaint)
            }
        }
    }

    private fun getDrawableWidth(): Float = width - LEFT_RIGHT_PADDING * 2

    private fun getAmplitudeScaleFactor(): Float {
        val maxAmplitude = height / 2.0f - TOP_BOTTOM_PADDING
        return INV_MAX_VALUE * maxAmplitude * zoomFactor
    }

    private fun setLineStrokeWidth(pixelWidth: Float) {
        linePaint.strokeWidth = if (pixelWidth >= 2.0f) pixelWidth else 1.0f
    }

    private fun drawProgressMarker(canvas: Canvas) {
        val drawableW = getDrawableWidth()
        val totalWidth = drawableW * zoomFactor
        val startX = (width - totalWidth) / 2
        val markerX = startX + progressFraction * totalWidth
        canvas.drawLine(
            markerX,
            TOP_BOTTOM_PADDING,
            markerX,
            height - TOP_BOTTOM_PADDING,
            markerPaint
        )
    }

    fun setData(buffer: ByteBuffer) {
        originalBuffer = buffer.slice()
        recomputeWaveform()
    }

    fun setProgress(progress: Float) {
        progressFraction = progress
        invalidate()
    }

    fun setZoom(zoom: Float) {
        val newZoom = zoom.coerceIn(1f, maxZoom)
        if (newZoom != zoomFactor) {
            zoomFactor = newZoom
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw && originalBuffer != null) {
            recomputeWaveform()
        }
    }

    private fun recomputeWaveform() {
        val buffer = originalBuffer ?: return
        val baseWidth = (width - LEFT_RIGHT_PADDING * 2).toInt().coerceAtLeast(100)
        if (baseWidth > 0) {
            waveForm = transformRawData(buffer, baseWidth)
            invalidate()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        if (!handled && event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            handled = true
        }
        return handled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}