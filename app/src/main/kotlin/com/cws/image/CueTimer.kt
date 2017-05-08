package com.cws.image

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.toImmutableList
import timber.log.Timber
import kotlin.properties.Delegates

//data class TimingValues(
//  val timerDurationMilliseconds: Int,
//  val elapsedTimeMilliseconds: Long
//)

//@BindingAdapter("initTimerDurationAndElapsedTimeMilliseconds")
//fun setInitTimerDurationAndElapsedTimeMilliseconds(
//  cueTimer: CueTimer,
//  timingValues: TimingValues
//) {
//  val (timerDurationMilliseconds, elapsedTimeMilliseconds) = timingValues
//
//  Timber.d("About to init timerDurationMilliseconds to ${timerDurationMilliseconds}")
//  Timber.d("About to init elapsedTimeMilliseconds to ${elapsedTimeMilliseconds}")
//  cueTimer.init(timerDurationMilliseconds, elapsedTimeMilliseconds)
//}

class CueTimer(
  context: Context,
  attrs: AttributeSet?,
  defStyleAttr: Int,
  defStyleRes: Int
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), Animator.AnimatorListener {
  constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
  ) : this(context, attrs, defStyleAttr, 0)
  constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
  constructor(context: Context) : this(context, null)

  private val cueTimerClockFace: CueTimerClockFace
  private val cueTimerNeedle: CueTimerNeedle

  private val sizeDefaultDp = 200F
  private val size: Float
  private val strokeWidthDefaultDp = 5F
  private val strokeWidth: Float
  private val startAngleDegreesDefault = 240F
  private val arcStartAngleDegrees: Float
  private val sweepAngleDegreesDefault = -330F
  private val arcSweepAngleDegrees: Float
  private val endMarkLengthRatioDefault = 0.2F
  private val endMarkLengthRatio: Float
  private val needleLengthRatioDefault = 0.65F
  private val needleLengthRatio: Float
  private val centerRadiusDefaultDp = strokeWidthDefaultDp
  private val centerRadius: Float
  var timerDurationMilliseconds = 0
    set(value) {
      field = value
      cueTimerClockFace.timerDurationMilliseconds = value
    }
  var elapsedTimeAtInitMilliseconds: Long by Delegates.notNull<Long>()
  val countdownAnimator: ObjectAnimator by lazy { makeCountdownAnimator() }
  var hasCompleted: Boolean by Delegates.notNull<Boolean>()

  var needleAngleDegrees = 0F
    set(value) {
      field = value
      cueTimerNeedle.thetaDegrees = value
    }

  init {
    val displayMetrics = context.resources.displayMetrics
    val a = context.obtainStyledAttributes(
      attrs, R.styleable.CueTimer, defStyleAttr, defStyleRes
    )
    size = a.getDimension(
        R.styleable.CueTimer_size,
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDefaultDp, displayMetrics)
      )
    strokeWidth = a.getDimension(
      R.styleable.CueTimer_stroke_width,
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, strokeWidthDefaultDp, displayMetrics)
    )
    endMarkLengthRatio = a.getFloat(R.styleable.CueTimer_end_mark_length_ratio,
                                    endMarkLengthRatioDefault)
    arcStartAngleDegrees = a.getFloat(R.styleable.CueTimer_start_angle_degrees,
                                      startAngleDegreesDefault)
    arcSweepAngleDegrees = a.getFloat(R.styleable.CueTimer_sweep_angle_degrees,
                                      sweepAngleDegreesDefault)
    needleLengthRatio = a.getFloat(R.styleable.CueTimer_needle_length_ratio,
                                   needleLengthRatioDefault)
    centerRadius = a.getDimension(
      R.styleable.CueTimer_center_radius,
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, centerRadiusDefaultDp, displayMetrics)
    )
    a.recycle()

    val radius = size / 2
    val center = PointF(radius, radius)

    cueTimerClockFace = CueTimerClockFace(
      context = context,
      size = size,
      strokeWidth = strokeWidth,
      center = center,
      arcRadius = radius,
      startAngleDegrees = arcStartAngleDegrees,
      sweepAngleDegrees = arcSweepAngleDegrees,
      endMarkLength = size * endMarkLengthRatio
      )

    cueTimerNeedle = CueTimerNeedle(
      context = context,
      center = center,
      radius = centerRadius,
      needleLength = radius * needleLengthRatio
    )

    addView(cueTimerClockFace)
    addView(cueTimerNeedle)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
  }

  fun needleAngleDegreesAtElapsedTime(
    arcStartAngleDegrees: Float,
    arcSweepAngleDegrees: Float,
    timerDurationMilliseconds: Int,
    elapsedTimeMilliseconds: Long
  ): Float {
    return (
        arcStartAngleDegrees.toDouble() +
        (
          arcSweepAngleDegrees.toDouble() * elapsedTimeMilliseconds.toDouble() /
          timerDurationMilliseconds.toDouble()
        )
      )
        .toFloat()
  }

  // 2017-05-07 Cort Spellman
  // Note that while a LinearInterpolator is the likely choice for the timer
  // animation the interpolation is left external to this view. This works
  // with initializing the timer with an externally-supplied duration and
  // elapsed time. (As opposed to saving the start time, to be recovered in case
  // of, say, a configuration change.)
  fun init(timerDurationMilliseconds: Int, elapsedTimeMilliseconds: Long) {
    Timber.d("About to set timerDurationMilliseconds to ${timerDurationMilliseconds}, set elapsedTimeAtInitMilliseconds to ${elapsedTimeMilliseconds}, and init needle")
    this.timerDurationMilliseconds = timerDurationMilliseconds
    if (elapsedTimeMilliseconds < timerDurationMilliseconds) {
      elapsedTimeAtInitMilliseconds = elapsedTimeMilliseconds
      hasCompleted = false
    }
    else {
      elapsedTimeAtInitMilliseconds = timerDurationMilliseconds.toLong()
      hasCompleted = true
    }

    Timber.d("About to init needle\n  arcStartAngleDegrees: ${arcStartAngleDegrees}\n  arcSweepAngleDegrees: ${arcSweepAngleDegrees}\n  timerDurationMilliseconds: ${timerDurationMilliseconds}\n  elapsedTimeAtInitMilliseconds: ${elapsedTimeAtInitMilliseconds}")
    val x = needleAngleDegreesAtElapsedTime(arcStartAngleDegrees,
                                            arcSweepAngleDegrees,
                                            timerDurationMilliseconds,
                                            elapsedTimeAtInitMilliseconds)
    Timber.d("Initing needleAngleDegrees to ${x}")
    needleAngleDegrees = x
  }

  private fun makeCountdownAnimator(): ObjectAnimator {
    assert(elapsedTimeAtInitMilliseconds < timerDurationMilliseconds)

    val animator = ObjectAnimator
      .ofFloat(this, "needleAngleDegrees", arcStartAngleDegrees + arcSweepAngleDegrees)
      .setDuration(timerDurationMilliseconds.toLong() - elapsedTimeAtInitMilliseconds)

    animator.addListener(this)

    return animator
  }

  override fun onAnimationEnd(animator: Animator) { hasCompleted = true }
  override fun onAnimationStart(animation: Animator?) { }
  override fun onAnimationCancel(animation: Animator?) { }
  override fun onAnimationRepeat(animation: Animator?) { }
}



class CueTimerClockFace(context: Context) : View(context) {
  private lateinit var center: PointF
  private var arcRadius: Float by Delegates.notNull<Float>()
  private var startAngleDegrees: Float by Delegates.notNull<Float>()
  private var sweepAngleDegrees: Float by Delegates.notNull<Float>()
  private var endAngleRadians: Double by Delegates.notNull<Double>()

  private var arcLeft: Float by Delegates.notNull<Float>()
  private var arcTop: Float by Delegates.notNull<Float>()
  private var arcRight: Float by Delegates.notNull<Float>()
  private var arcBottom: Float by Delegates.notNull<Float>()

  private lateinit var endMarkStart: PointF
  private lateinit var endMarkEnd: PointF

  private var numbersRadius: Float by Delegates.notNull<Float>()
  private var textOffset: Float by Delegates.notNull<Float>()
  private lateinit var countdownSecondsPoints: ImmutableList<PointF>
  private var timerDurationSeconds = 0
  private var numberRadiansPerSecond = 0.0
  var timerDurationMilliseconds = 0
    set(value) {
      field = value

      timerDurationSeconds = value / 1000

      numberRadiansPerSecond = if (value > 0) {
        Math.toRadians((sweepAngleDegrees / timerDurationSeconds).toDouble())
      }
      else {
        0.0
      }

      countdownSecondsPoints = (1..timerDurationSeconds).map { i ->
        translatePointF(
          center.x,
          center.y - textOffset,
          rotatePointF(endAngleRadians,
                       PointF(numbersRadius * Math.cos(
                         -numberRadiansPerSecond * i).toFloat(),
                              numbersRadius * Math.sin(
                                -numberRadiansPerSecond * i).toFloat()
                       )
          )
        )
      }
        .toImmutableList()

      invalidate()
    }

  private var arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var numbersPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  constructor(
    context: Context,
    size: Float,
    strokeWidth: Float,
    center: PointF,
    arcRadius: Float,
    startAngleDegrees: Float,
    sweepAngleDegrees: Float,
    endMarkLength: Float
  ): this(context) {
    this.center = center
    this.arcRadius = arcRadius
    this.startAngleDegrees = startAngleDegrees
    this.sweepAngleDegrees = sweepAngleDegrees
    this.endAngleRadians = Math.toRadians((startAngleDegrees + sweepAngleDegrees).toDouble())

    arcLeft = strokeWidth
    arcTop = strokeWidth
    arcRight = size - strokeWidth
    arcBottom = size - strokeWidth

    val endMarkStartBeforeXf = PointF(center.x - strokeWidth / 2, 0F)
    endMarkStart =
      translatePointF(
        center.x,
        center.y,
        rotatePointF(endAngleRadians, endMarkStartBeforeXf)
      )
    endMarkEnd =
      translatePointF(
        center.x,
        center.y,
        rotatePointF(endAngleRadians, PointF(endMarkStartBeforeXf.x - endMarkLength, 0F))
      )

    arcPaint.color = ContextCompat.getColor(context, R.color.colorPrimary)
    arcPaint.style = Paint.Style.STROKE
    arcPaint.strokeWidth = strokeWidth

    numbersPaint.color = ContextCompat.getColor(context, R.color.colorPrimaryText)
    numbersPaint.textAlign = Paint.Align.CENTER
    numbersPaint.textSize =
      TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        Math.max(size / 30, 12F),
        context.resources.displayMetrics
      )
    numbersPaint.style = Paint.Style.FILL
    numbersRadius = arcRadius - numbersPaint.textSize * 1.25F
    textOffset = (numbersPaint.ascent() + numbersPaint.descent()) / 2
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawArc(arcLeft,
                   arcTop,
                   arcRight,
                   arcBottom,
                   startAngleDegrees,
                   sweepAngleDegrees,
                   false,
                   arcPaint
    )

    canvas.drawLine(endMarkStart.x,
                    endMarkStart.y,
                    endMarkEnd.x,
                    endMarkEnd.y,
                    arcPaint
    )


    if (timerDurationSeconds > 0) {
      (1..timerDurationSeconds).forEach { i ->
        val idx = i - 1
        canvas.drawText(
          i.toString(),
          countdownSecondsPoints[idx].x,
          countdownSecondsPoints[idx].y,
          numbersPaint
        )
      }
    }
  }
}



class CueTimerNeedle(context: Context) : View(context) {
  private lateinit var center: PointF
  private var radius: Float by Delegates.notNull<Float>()

  var thetaDegrees = 0F
    set(value) {
      if (field != value) {
        field = value
        thetaRadians = Math.toRadians(value.toDouble())
        invalidate()
      }
    }
  private var thetaRadians: Double = Math.toRadians(thetaDegrees.toDouble())
  private lateinit var needlePoint1: PointF
  private lateinit var needlePoint2: PointF
  private lateinit var needlePoint3: PointF
  private val needlePath = Path()

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  constructor(
    context: Context,
    center: PointF,
    radius: Float,
    needleLength: Float
  ) : this(context) {
    this.center = center
    this.radius = radius

    needlePoint1 = PointF(0F, -radius)
    needlePoint2 = PointF(0F, radius)
    needlePoint3 = PointF(needleLength, 0F)

    paint.color = ContextCompat.getColor(context, R.color.colorPrimary)
    paint.style = Paint.Style.FILL
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawCircle(center.x, center.y, radius, paint)
    canvas.drawPath(
      rotatePath(needlePath, center, thetaRadians, needlePoint1, needlePoint2, needlePoint3),
      paint
    )
  }

  fun rotatePath(path: Path, point: PointF, angleRadians: Double, vararg points: PointF): Path {
    path.reset()

    val xfPts = points.map { p ->
      translatePointF(point.x, point.y, rotatePointF(angleRadians, p))
    }

    path.moveTo(xfPts.first().x, xfPts.first().y)
    xfPts.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
    path.close()

    return path
  }
}

fun translatePointF(deltaX: Float, deltaY: Float, point: PointF): PointF {
  // 2017-05-01 Cort Spellman
  // If performance becomes a problem, then represent translation in 2-space as
  // a shear in 3-space and compose the rotation and shearing matrices into a
  // a single transformation matrix.
  // For now, though, it's easier to translate by translating.
  return PointF(point.x + deltaX, point.y + deltaY)
}

fun rotatePointF(thetaRadians: Double, p: PointF): PointF {
  // 2017-04-30 Cort Spellman
  // Unless performance becomes a problem, rotate the original points every
  // time. This does require creating point objects in onDraw; creating
  // objects is NOT recommended inside onDraw.
  // An alternative would be to provide delta angles and mutate the points,
  // rotating by the delta every time.
  return PointF(
    (p.x * Math.cos(thetaRadians) - p.y * Math.sin(thetaRadians)).toFloat(),
    (p.x * Math.sin(thetaRadians) + p.y * Math.cos(thetaRadians)).toFloat()
  )
}
