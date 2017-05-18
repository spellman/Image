package com.cws.image

import android.animation.ObjectAnimator
import android.content.Context
import android.databinding.BindingAdapter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.github.andrewoma.dexx.kollection.toImmutableList
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import kotlin.properties.Delegates

@BindingAdapter("timerDurationMilliseconds")
fun setTimerDurationMilliseconds(cueTimer: CueTimer, timerDurationMilliseconds: Int) {
  cueTimer.timerDurationMillisecondsStream.onNext(timerDurationMilliseconds)
}

@BindingAdapter("elapsedTimeMilliseconds")
fun setElapsedTimeMilliseconds(cueTimer: CueTimer, elapsedTimeMilliseconds: Long) {
  cueTimer.rawElapsedTimeAtInitMillisecondsStream.onNext(elapsedTimeMilliseconds)
}

class CueTimer(
  context: Context,
  attrs: AttributeSet?,
  defStyleAttr: Int,
  defStyleRes: Int
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {
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
  private val colorDefault = android.graphics.Color.BLACK
  private val outerStrokeColor: Int
  private val textColor: Int
  private val needleColor: Int

  val timerDurationMillisecondsStream: PublishSubject<Int> = PublishSubject.create()
  val rawElapsedTimeAtInitMillisecondsStream: PublishSubject<Long> = PublishSubject.create()
  private var timerDurationMilliseconds: Int by Delegates.notNull<Int>()
  private var elapsedTimeAtInitMilliseconds: Long by Delegates.notNull<Long>()
  val countdownAnimator: ObjectAnimator by lazy { makeCountdownAnimator() }
  val hasInitialized: PublishSubject<Unit> = PublishSubject.create()

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
    outerStrokeColor = a.getColor(R.styleable.CueTimer_outer_stroke_color, colorDefault)
    textColor = a.getColor(R.styleable.CueTimer_text_color, colorDefault)
    needleColor = a.getColor(R.styleable.CueTimer_needle_color, colorDefault)

    a.recycle()

    cueTimerClockFace = CueTimerClockFace(
      context = context,
      strokeWidth = strokeWidth,
      startAngleDegrees = arcStartAngleDegrees,
      sweepAngleDegrees = arcSweepAngleDegrees,
      endMarkLengthRatio = endMarkLengthRatio,
      outerStrokeColor = outerStrokeColor,
      textColor = textColor
      )

    cueTimerNeedle = CueTimerNeedle(
      context = context,
      centerRadius = centerRadius,
      needleLengthRatio = needleLengthRatio,
      needleColor = needleColor
    )

    addView(cueTimerClockFace)
    addView(cueTimerNeedle)

    // 2017-05-17 Cort Spellman
    // I'm zipping to synchronize the streams:
    // When the timer duration and elapsed time are set via data binding, the
    // binding adapter functions called with the supplied XML attributes are
    // called serially.
    // I could just bind a tuple with both the timer duration and elapsed time
    // but this seems nicer and doesn't require a view model (each attribute
    // could be set to a hard-coded value).
    //
    // 2017-05-17 Cort spellman
    // PROBLEM: The binding adapter functions are each called once with a zero
    // value for the data type. WHY?
    // * I have not seen any documentation of this behavior.
    // * Is this subject to change in the future?
    // As a workaround, I'm filtering elapsed times for positive values.
    // NOTE: init must therefore be called programmatically to set a timer
    // duration of zero; the filter currently prevents setting the duration to
    // zero via data binding.
    Observable.zip(
      timerDurationMillisecondsStream.filter {x -> x > 0},
      rawElapsedTimeAtInitMillisecondsStream,
      BiFunction { timerDurationMilliseconds: Int, elapsedTimeMilliseconds: Long ->
        Pair(timerDurationMilliseconds, elapsedTimeMilliseconds)
      }
    )
      .subscribe { (timerDurationMilliseconds, elapsedTimeMilliseconds) ->
        init(timerDurationMilliseconds, elapsedTimeMilliseconds)
      }

    Observable.combineLatest(
      cueTimerClockFace.hasInitialized,
      cueTimerNeedle.hasInitialized,
      BiFunction { _: Unit, _: Unit -> Unit }
    )
      .subscribe { _ ->
        hasInitialized.onNext(Unit)
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
  // animation, the interpolation is left external to this view. I think this
  // goes well with initializing the timer with an externally-supplied duration
  // and elapsed time. (As opposed to saving the start time, to be recovered in
  // case of, say, a configuration change.)
  fun init(timerDurationMilliseconds: Int, rawElapsedTimeMilliseconds: Long) {
    Timber.d("CueTimer#init started with timerDurationMilliseconds: ${timerDurationMilliseconds}, rawElapsedTimeMilliseconds: ${rawElapsedTimeMilliseconds}")
    val elapsedTimeMilliseconds =
      if (rawElapsedTimeMilliseconds < timerDurationMilliseconds) {
        rawElapsedTimeMilliseconds
      }
      else {
        timerDurationMilliseconds.toLong()
      }

    val x = needleAngleDegreesAtElapsedTime(arcStartAngleDegrees,
                                            arcSweepAngleDegrees,
                                            timerDurationMilliseconds,
                                            elapsedTimeMilliseconds)

    Timber.d("About to set CueTimer#needleAngleDegrees to ${x} from CueTimer#init")
    needleAngleDegrees = x

    Timber.d("About to set CueTimer#elapsedTimeAtInitMilliseconds to ${elapsedTimeMilliseconds} from CueTimer#init")
    this.elapsedTimeAtInitMilliseconds = elapsedTimeMilliseconds

    Timber.d("About to set CueTimer#timerDurationMilliseconds to ${timerDurationMilliseconds} from CueTimer#init")
    this.timerDurationMilliseconds = timerDurationMilliseconds

    Timber.d("About to call CueTimerClockFace#setTimerDuration with ${timerDurationMilliseconds} from CueTimer#init")
    cueTimerClockFace.setTimerDuration(timerDurationMilliseconds)
  }

  private fun makeCountdownAnimator(): ObjectAnimator {
    val animator = ObjectAnimator
      .ofFloat(this, "needleAngleDegrees", arcStartAngleDegrees + arcSweepAngleDegrees)
      .setDuration(timerDurationMilliseconds.toLong() - elapsedTimeAtInitMilliseconds)

    return animator
  }
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
  private val endMarkStart = PointF(0F, 0F)
  private val endMarkEnd = PointF(0F, 0F)

  private var numbersRadius: Float by Delegates.notNull<Float>()
  private var textOffset: Float by Delegates.notNull<Float>()
  private var timerDurationSeconds: Int by Delegates.notNull<Int>()
  private lateinit var countdownSecondsPoints: ImmutableList<PointF>
  private var arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var numbersPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  data class ArcDefinition(val center: PointF, val arcRadius: Float)
  val arcDefinitionStream: PublishSubject<ArcDefinition> = PublishSubject.create()
  val timerDurationMillisecondsStream: PublishSubject<Int> = PublishSubject.create()

  val hasInitialized: PublishSubject<Unit> = PublishSubject.create()

  constructor(
    context: Context,
    strokeWidth: Float,
    startAngleDegrees: Float,
    sweepAngleDegrees: Float,
    endMarkLengthRatio: Float,
    outerStrokeColor: Int,
    textColor: Int
  ): this(context) {
    this.strokeWidth = strokeWidth
    this.startAngleDegrees = startAngleDegrees
    this.sweepAngleDegrees = sweepAngleDegrees
    this.endAngleRadians = Math.toRadians((startAngleDegrees + sweepAngleDegrees).toDouble())
    this.endMarkLengthRatio = endMarkLengthRatio

    arcPaint.color = outerStrokeColor
    arcPaint.style = Paint.Style.STROKE
    arcPaint.strokeWidth = strokeWidth

    numbersPaint.color = textColor
    numbersPaint.textAlign = Paint.Align.CENTER
    numbersPaint.style = Paint.Style.FILL

    Observable.combineLatest(
      arcDefinitionStream,
      timerDurationMillisecondsStream,
      BiFunction { (center, arcRadius): ArcDefinition, timerDurationMilliseconds: Int ->
        Triple(center, arcRadius, timerDurationMilliseconds)
      }
    )
      .subscribe { (center, arcRadius, timerDurationMilliseconds) ->
        Timber.d("New arc definition or timer duration for CueTimerClockFace.\n  center: ${center}, arcRadius: ${arcRadius}, timerDurationMilliseconds: ${timerDurationMilliseconds}")
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

        rotatePointF(endAngleRadians, endMarkStartBeforeXf, endMarkStart)
        translatePointF(center.x, center.y, endMarkStart, endMarkStart)

        rotatePointF(endAngleRadians, endMarkEndBeforeXf, endMarkEnd)
        translatePointF(center.x, center.y, endMarkEnd, endMarkEnd)

        timerDurationSeconds = timerDurationMilliseconds / 1000

        val radiansPerSecond = if (timerDurationMilliseconds > 0) {
          Math.toRadians((sweepAngleDegrees / timerDurationSeconds).toDouble())
        }
        else {
          0.0
        }

        countdownSecondsPoints =
          (1..timerDurationSeconds)
            .map { _ -> PointF(0F, 0F) }
            .toImmutableList()

        for (i in countdownSecondsPoints.indices) {
          rotatePointF(endAngleRadians,
                       PointF(
                         numbersRadius * Math.cos(-radiansPerSecond * (i + 1)).toFloat(),
                         numbersRadius * Math.sin(-radiansPerSecond * (i + 1)).toFloat()
                       ),
                       countdownSecondsPoints[i]
          )
          translatePointF(
            center.x,
            center.y - textOffset,
            countdownSecondsPoints[i],
            countdownSecondsPoints[i]
          )
        }

        Timber.d("About to put value on CueTimerClockFace#hasInitialized stream.")
        hasInitialized.onNext(Unit)
        invalidate()
      }
  }

  fun setTimerDuration(timerDurationMilliseconds: Int) {
    timerDurationMillisecondsStream.onNext(timerDurationMilliseconds)
  }

  fun setDimensions(center: PointF, arcRadius: Float) {
    arcDefinitionStream.onNext(ArcDefinition(center, arcRadius))
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
  private lateinit var needlePoints: ImmutableList<PointF>
  private lateinit var xfPoints: ImmutableList<PointF>
  private val needlePath = Path()

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  val hasInitialized: PublishSubject<Unit> = PublishSubject.create()

  constructor(
    context: Context,
    centerRadius: Float,
    needleLengthRatio: Float,
    needleColor: Int
  ) : this(context) {
    this.centerRadius = centerRadius
    this.needleLengthRatio = needleLengthRatio

    paint.color = needleColor
    paint.style = Paint.Style.FILL
  }

  fun setDimensions(center: PointF, arcRadius: Float) {
    Timber.d("New measurements for CueTimerNeedle\n  center: ${center}, arcRadius: ${arcRadius}")
    this.center = center

    val needleLength = arcRadius * needleLengthRatio

    needlePoints = immutableListOf(
      PointF(0F, -centerRadius),
      PointF(0F, centerRadius),
      PointF(needleLength, 0F)
    )
    xfPoints = needlePoints.map { _ -> PointF(0F, 0F) }.toImmutableList()

    Timber.d("About to put value on CueTimerNeedle#hasInitialized stream.")
    hasInitialized.onNext(Unit)
  }

  override fun onDraw(canvas: Canvas) {
    canvas.drawCircle(center.x, center.y, centerRadius, paint)
    rotatePath(needlePath, center, thetaRadians, needlePoints, xfPoints)
    canvas.drawPath(needlePath, paint)
  }
}

fun rotatePath(
  path: Path,
  point: PointF,
  angleRadians: Double,
  inPathPoints: ImmutableList<PointF>,
  outPathPoints: ImmutableList<PointF>
) {
  assert(inPathPoints.count() == outPathPoints.count())
  path.reset()

  for (i in inPathPoints.indices) { rotatePointF(angleRadians, inPathPoints[i], outPathPoints[i]) }
  for (i in outPathPoints.indices) { translatePointF(point.x, point.y, outPathPoints[i], outPathPoints[i]) }

  path.moveTo(outPathPoints.first().x, outPathPoints.first().y)
  outPathPoints.drop(1).forEach { p -> path.lineTo(p.x, p.y) }
  path.close()
}

fun translatePointF(deltaX: Float, deltaY: Float, inPoint: PointF, outPoint: PointF) {
  outPoint.x = inPoint.x + deltaX
  outPoint.y = inPoint.y + deltaY
}

fun rotatePointF(thetaRadians: Double, inPoint: PointF, outPoint: PointF) {
  val xOld = inPoint.x
  val yOld = inPoint.y

  val xNew = (xOld * Math.cos(thetaRadians)
              - yOld * Math.sin(thetaRadians)
             ).toFloat()
  val yNew = (xOld * Math.sin(thetaRadians)
              + yOld * Math.cos(thetaRadians)
             ).toFloat()

  outPoint.x = xNew
  outPoint.y = yNew
}
