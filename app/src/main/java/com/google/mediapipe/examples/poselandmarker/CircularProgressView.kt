package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Custom view to display a circular progress indicator with percentage text in the center
 */
class CircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Progress value (0-100)
    private var progress = 0
    
    // Text to display in center
    private var centerText = "0%"
    
    // Colors
    private var progressColor = Color.parseColor("#007F8B") // mp_color_primary
    private var backgroundColor = Color.parseColor("#DDDDDD") // Light gray
    private var textColor = Color.BLACK
    
    // Paint objects
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.STROKE
        strokeWidth = 20f
    }
    
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = progressColor
        style = Paint.Style.STROKE
        strokeWidth = 20f
    }
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 50f
        textAlign = Paint.Align.CENTER
    }
    
    // Drawing bounds
    private val rectF = RectF()
    
    /**
     * Set the progress value (0-100)
     */
    fun setProgress(progress: Int) {
        this.progress = progress.coerceIn(0, 100)
        invalidate()
    }
    
    /**
     * Set the text to display in the center
     */
    fun setText(text: String) {
        this.centerText = text
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Calculate the bounds for the circle
        val padding = backgroundPaint.strokeWidth / 2
        rectF.set(
            padding,
            padding,
            width - padding,
            height - padding
        )
        
        // Draw background circle
        canvas.drawArc(rectF, 0f, 360f, false, backgroundPaint)
        
        // Draw progress arc
        val sweepAngle = (progress / 100f) * 360f
        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)
        
        // Draw center text
        val xPos = width / 2f
        val yPos = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(centerText, xPos, yPos, textPaint)
    }
}