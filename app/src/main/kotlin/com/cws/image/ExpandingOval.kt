package com.cws.image

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.github.andrewoma.dexx.kollection.immutableListOf
import kotlin.properties.Delegates

class ExpandingOval(
  context: Context,
  attrs: AttributeSet?,
  defStyleAttr: Int,
  defStyleRes: Int
) : View(context, attrs, defStyleAttr, defStyleRes) {
  constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
  ) : this(context, attrs, defStyleAttr, 0)
  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
  constructor(context: Context) : this(context, null)

  private val colorDefault = android.graphics.Color.BLACK
  private val color: Int
  private val strokeWidthDefaultDp = 4F
  private val strokeWidth: Float
  private val ovalWidthInitialDefault = 100F
  private val ovalWidthInitial: Float
  private val ovalHeightInitialDefault = 100F
  private val ovalHeightInitial: Float
  private val ovalWidthFinal: Float
  private val ovalHeightFinal: Float
  private val fadeInDimensionScaleFactorFinalDefault = 1F
  private val fadeInDimensionScaleFactorFinal: Float
  private val fadeInDurationDefault = 0
  private val fadeInDuration: Long
  private val fadeInAlphaFinalDefault = 1F
  private val fadeInAlphaFinal: Float
  private val fadeOutDimensionScaleFactorFinalDefault = 1F
  private val fadeOutDimensionScaleFactorFinal: Float
  private val fadeOutDurationDefault = 0
  private val fadeOutDuration: Long

  fun makeRadius(length: Float, scaleFactor: Float): Float {
    return length * scaleFactor / 2
  }

  fun makeXRadius(): Float {
    return makeRadius(ovalWidthInitial, scaleFactor)
  }

  fun makeYRadius(): Float {
    return makeRadius(ovalHeightInitial, scaleFactor)
  }

  fun makeOvalBorder(centerX: Float, centerY: Float, xRadius: Float, yRadius: Float) {
    ovalLeft = centerX - xRadius
    ovalRight = centerX + xRadius
    ovalTop = centerY - yRadius
    ovalBottom = centerY + yRadius
  }

  private val alphaInitial = 0F
  var myAlpha: Float = alphaInitial
    set(value) {
      field = value
      paint.alpha = (value * 256).toInt()
      invalidate()
    }
  private val scaleFactorInitial = 1F
  var scaleFactor: Float = scaleFactorInitial
    set(value) {
      field = value
      makeOvalBorder(centerX, centerY, makeXRadius(), makeYRadius())
      invalidate()
    }

  private var centerX by Delegates.notNull<Float>()
  private var centerY by Delegates.notNull<Float>()
  private var ovalLeft by Delegates.notNull<Float>()
  private var ovalRight by Delegates.notNull<Float>()
  private var ovalTop by Delegates.notNull<Float>()
  private var ovalBottom by Delegates.notNull<Float>()

  val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  init {
    val displayMetrics = context.resources.displayMetrics
    val a = context.obtainStyledAttributes(
      attrs, R.styleable.ExpandingOval, defStyleAttr, defStyleRes
    )
    color = a.getColor(R.styleable.ExpandingOval_color, colorDefault)
    strokeWidth = a.getDimension(
      R.styleable.CueTimer_stroke_width,
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, strokeWidthDefaultDp, displayMetrics)
    )
    ovalWidthInitial = a.getDimension(
      R.styleable.ExpandingOval_width_initial,
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, ovalWidthInitialDefault, displayMetrics)
    )
    ovalHeightInitial = a.getDimension(
      R.styleable.ExpandingOval_height_initial,
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, ovalHeightInitialDefault, displayMetrics)
    )
    fadeInDimensionScaleFactorFinal =
      a.getFloat(R.styleable.ExpandingOval_fade_in_dimension_scale_factor_final,
                 fadeInDimensionScaleFactorFinalDefault)
    fadeInDuration =
      a.getInteger(R.styleable.ExpandingOval_fade_in_duration,
                   fadeInDurationDefault)
        .toLong()
    fadeInAlphaFinal =
      a.getFloat(R.styleable.ExpandingOval_fade_in_alpha_final,
                 fadeInAlphaFinalDefault)
    fadeOutDimensionScaleFactorFinal =
      a.getFloat(R.styleable.ExpandingOval_fade_out_dimension_scale_factor_final,
                 fadeOutDimensionScaleFactorFinalDefault)
    fadeOutDuration =
      a.getInteger(R.styleable.ExpandingOval_fade_out_duration,
                   fadeOutDurationDefault).toLong()

    ovalWidthFinal = ovalWidthInitial * fadeOutDimensionScaleFactorFinal
    ovalHeightFinal = ovalHeightInitial * fadeOutDimensionScaleFactorFinal

    a.recycle()

    paint.color = color
    paint.alpha = (myAlpha * 256).toInt()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = strokeWidth
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val desiredWidth = suggestedMinimumWidth + paddingLeft + paddingRight
    val desiredHeight = suggestedMinimumHeight + paddingTop + paddingBottom

    val widths = immutableListOf(desiredWidth.toFloat(), ovalWidthInitial, ovalWidthFinal)
    val heights = immutableListOf(desiredHeight.toFloat(), ovalHeightInitial, ovalHeightFinal)

    setMeasuredDimension(
      resolveSize((widths.max() as Float).toInt(), widthMeasureSpec),
      resolveSize((heights.max() as Float).toInt(), heightMeasureSpec)
    )
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    centerX = width.toFloat() / 2
    centerY = height.toFloat() / 2
    makeOvalBorder(centerX, centerY, makeXRadius(), makeYRadius())
    super.onLayout(changed, l, t, r, b)
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawOval(ovalLeft, ovalTop, ovalRight, ovalBottom, paint)
  }

  fun makeAnimator(): AnimatorSet {
    val fadeIn = ObjectAnimator.ofPropertyValuesHolder(
      this,
      PropertyValuesHolder.ofFloat(
        "scaleFactor", scaleFactorInitial, fadeInDimensionScaleFactorFinal
      ),
      PropertyValuesHolder.ofFloat("myAlpha", alphaInitial, fadeInAlphaFinal)
    )
    fadeIn.duration = fadeInDuration
    fadeIn.interpolator = DecelerateInterpolator()

    val fadeOut = ObjectAnimator.ofPropertyValuesHolder(
      this,
      PropertyValuesHolder.ofFloat(
        "scaleFactor", fadeInDimensionScaleFactorFinal, fadeOutDimensionScaleFactorFinal
      ),
      PropertyValuesHolder.ofFloat("myAlpha", fadeInAlphaFinal, alphaInitial)
    )
    fadeOut.duration = fadeOutDuration
    fadeOut.interpolator = LinearInterpolator()

    val animatorSet = AnimatorSet()
    animatorSet.playSequentially(fadeIn, fadeOut)
    return animatorSet
  }
}

