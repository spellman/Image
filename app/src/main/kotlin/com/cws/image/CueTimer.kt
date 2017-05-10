package com.cws.image

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.databinding.BindingAdapter
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
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import kotlin.properties.Delegates

@BindingAdapter("timerDurationMilliseconds")
fun setTimerDurationMilliseconds(cueTimer: CueTimer, timerDurationMilliseconds: Int) {
  Timber.d("About to init timerDurationMilliseconds to ${timerDurationMilliseconds}")
  cueTimer.timerDurationMillisecondsStream.onNext(timerDurationMilliseconds)
}

@BindingAdapter("elapsedTimeMilliseconds")
fun setElapsedTimeMilliseconds(cueTimer: CueTimer, elapsedTimeMilliseconds: Long) {
  Timber.d("About to init elapsedTimeMilliseconds to ${elapsedTimeMilliseconds}")
  cueTimer.elapsedTimeMillisecondsStream.onNext(elapsedTimeMilliseconds)
}

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

  private var center = PointF(0F, 0F)
  private val strokeWidthDefaultDp = 5F
  private val strokeWidth: Float
  private val startAngleDegreesDefault = 240F
  private val arcStartAngleDegrees: Float
  private val sweepAngleDegreesDefault = -330F
  private val arcSweepAngleDegrees: Float
  private val endMarkLengthRatioDefault = 0.45F
  private val endMarkLengthRatio: Float
  private val needleLengthRatioDefault = 0.65F
  private val needleLengthRatio: Float
  private val centerRadiusDefaultDp = strokeWidthDefaultDp
  private val centerRadius: Float

  val timerDurationMillisecondsStream: PublishSubject<Int> = PublishSubject.create()
  val elapsedTimeMillisecondsStream: PublishSubject<Long> = PublishSubject.create()
  private var timerDurationMilliseconds = 0
    set(value) {
      field = value
      cueTimerClockFace.setTimerDuration(value)
    }
  private var elapsedTimeAtInitMilliseconds: Long by Delegates.notNull<Long>()
  val countdownAnimator: ObjectAnimator by lazy { makeCountdownAnimator() }
  val hasCompleted: PublishSubject<Boolean> = PublishSubject.create()

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

    cueTimerClockFace = CueTimerClockFace(
      context = context,
      strokeWidth = strokeWidth,
      startAngleDegrees = arcStartAngleDegrees,
      sweepAngleDegrees = arcSweepAngleDegrees,
      endMarkLengthRatio = endMarkLengthRatio
      )

    cueTimerNeedle = CueTimerNeedle(
      context = context,
      centerRadius = centerRadius,
      needleLengthRatio = needleLengthRatio
    )

    addView(cueTimerClockFace)
    addView(cueTimerNeedle)

    Observable.combineLatest(
      timerDurationMillisecondsStream,
      elapsedTimeMillisecondsStream,
      BiFunction { timerDurationMilliseconds: Int, elapsedTimeMilliseconds: Long ->
        Pair(timerDurationMilliseconds, elapsedTimeMilliseconds)
      }
    )
      .subscribe { (timerDurationMilliseconds, elapsedTimeMilliseconds) ->
        init(timerDurationMilliseconds, elapsedTimeMilliseconds)
      }
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    if (!changed) {
      return
    }

    center.x = width / 2F
    center.y = height / 2F
    val arcRadius = Math.min(center.x, center.y)

    cueTimerClockFace.setDimensions(center, arcRadius)
    cueTimerNeedle.setDimensions(center, arcRadius)

    super.onLayout(changed, left, top, right, bottom)
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
      hasCompleted.onNext(false)
    }
    else {
      elapsedTimeAtInitMilliseconds = timerDurationMilliseconds.toLong()
      hasCompleted.onNext(true)
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
    assert(elapsedTimeAtInitMilliseconds <= timerDurationMilliseconds)

    val animator = ObjectAnimator
      .ofFloat(this, "needleAngleDegrees", arcStartAngleDegrees + arcSweepAngleDegrees)
      .setDuration(timerDurationMilliseconds.toLong() - elapsedTimeAtInitMilliseconds)

    animator.addListener(this)

    return animator
  }

  override fun onAnimationEnd(animator: Animator) { hasCompleted.onNext(true) }
  override fun onAnimationStart(animation: Animator?) { }
  override fun onAnimationCancel(animation: Animator?) { }
  override fun onAnimationRepeat(animation: Animator?) { }
}



class CueTimerClockFace(context: Context) : View(context) {
  private lateinit var center: PointF
  private var strokeWidth: Float by Delegates.notNull<Float>()
  private var startAngleDegrees: Float by Delegates.notNull<Float>()
  private var sweepAngleDegrees: Float by Delegates.notNull<Float>()
  private var endAngleRadians: Double by Delegates.notNull<Double>()

  private var arcLeft: Float by Delegates.notNull<Float>()
  private var arcTop: Float by Delegates.notNull<Float>()
  private var arcRight: Float by Delegates.notNull<Float>()
  private var arcBottom: Float by Delegates.notNull<Float>()

  private var endMarkLengthRatio: Float by Delegates.notNull<Float>()
  private lateinit var endMarkStart: PointF
  private lateinit var endMarkEnd: PointF

  private var numbersRadius: Float by Delegates.notNull<Float>()
  private var textOffset: Float by Delegates.notNull<Float>()
  private lateinit var countdownSecondsPoints: ImmutableList<PointF>
  private var timerDurationSeconds: Int by Delegates.notNull<Int>()
  private var arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var numbersPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  val centerStream: PublishSubject<PointF> = PublishSubject.create<PointF>()
  val arcRadiusStream: PublishSubject<Float> = PublishSubject.create<Float>()
  val timerDurationMillisecondsStream: PublishSubject<Int> = PublishSubject.create<Int>()

  constructor(
    context: Context,
    strokeWidth: Float,
    startAngleDegrees: Float,
    sweepAngleDegrees: Float,
    endMarkLengthRatio: Float
  ): this(context) {
    this.strokeWidth = strokeWidth
    this.startAngleDegrees = startAngleDegrees
    this.sweepAngleDegrees = sweepAngleDegrees
    this.endAngleRadians = Math.toRadians((startAngleDegrees + sweepAngleDegrees).toDouble())
    this.endMarkLengthRatio = endMarkLengthRatio

    arcPaint.color = ContextCompat.getColor(context, R.color.colorPrimary)
    arcPaint.style = Paint.Style.STROKE
    arcPaint.strokeWidth = strokeWidth

    numbersPaint.color = ContextCompat.getColor(context, R.color.colorPrimaryText)
    numbersPaint.textAlign = Paint.Align.CENTER
    numbersPaint.style = Paint.Style.FILL

    Observable.combineLatest(
      centerStream,
      arcRadiusStream,
      timerDurationMillisecondsStream,
      Function3 { center: PointF, arcRadius: Float, timerDurationMilliseconds: Int ->
        Triple(center, arcRadius, timerDurationMilliseconds)
      }
    )
      .subscribe { (center, arcRadius, timerDurationMilliseconds) ->
        this.center = center

        val arcSize = arcRadius * 2
        arcLeft = center.x - arcRadius + strokeWidth
        arcRight = center.x + arcRadius - strokeWidth
        arcTop = center.y - arcRadius + strokeWidth
        arcBottom = center.y + arcRadius - strokeWidth

        numbersPaint.textSize =
          TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            Math.max(arcSize / 30, 12F),
            context.resources.displayMetrics
          )
        numbersRadius = arcRadius - numbersPaint.textSize * 1.25F
        textOffset = (numbersPaint.ascent() + numbersPaint.descent()) / 2

        val endMarkLength = arcRadius * endMarkLengthRatio
        val endMarkStartBeforeXf = PointF(arcRadius - strokeWidth / 2, 0F)
        val endMarkEndBeforeXf = PointF(endMarkStartBeforeXf.x - endMarkLength, 0F)
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
            rotatePointF(endAngleRadians, endMarkEndBeforeXf)
          )

        Timber.d("End mark start before xf: ${endMarkStartBeforeXf}")
        Timber.d("End mark end before xf: ${endMarkEndBeforeXf}")
        Timber.d("End mark start: ${endMarkStart}")
        Timber.d("End mark end: ${endMarkEnd}")

        timerDurationSeconds = timerDurationMilliseconds / 1000

        val radiansPerSecond = if (timerDurationMilliseconds > 0) {
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
                         PointF(
                           numbersRadius * Math.cos(-radiansPerSecond * i).toFloat(),
                           numbersRadius * Math.sin(-radiansPerSecond * i).toFloat()
                         )
            )
          )
        }
          .toImmutableList()

        invalidate()
      }
  }

  fun setTimerDuration(timerDurationMilliseconds: Int) {
    timerDurationMillisecondsStream.onNext(timerDurationMilliseconds)
  }

  fun setDimensions(center: PointF, arcRadius: Float) {
    centerStream.onNext(center)
    arcRadiusStream.onNext(arcRadius)
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
  private var centerRadius: Float by Delegates.notNull<Float>()
  private var needleLengthRatio: Float by Delegates.notNull<Float>()

  var thetaDegrees = 0F
    set(value) {
      if (field != value) {
        field = value
        thetaRadians = Math.toRadians(value.toDouble())
        invalidate()
      }
    }
  private var thetaRadians: Double = Math.toRadians(thetaDegrees.toDouble())
  private var needlePoint1 = PointF(0F, 0F)
  private var needlePoint2 = PointF(0F, 0F)
  private var needlePoint3 = PointF(0F, 0F)
  private val needlePath = Path()

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  constructor(
    context: Context,
    centerRadius: Float,
    needleLengthRatio: Float
  ) : this(context) {
    this.centerRadius = centerRadius
    this.needleLengthRatio = needleLengthRatio

    paint.color = ContextCompat.getColor(context, R.color.colorPrimary)
    paint.style = Paint.Style.FILL
  }

  fun setDimensions(center: PointF, arcRadius: Float) {
    this.center = center

    val needleLength = arcRadius * needleLengthRatio
    needlePoint1 = PointF(0F, -centerRadius)
    needlePoint2 = PointF(0F, centerRadius)
    needlePoint3 = PointF(needleLength, 0F)
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawCircle(center.x, center.y, centerRadius, paint)
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
