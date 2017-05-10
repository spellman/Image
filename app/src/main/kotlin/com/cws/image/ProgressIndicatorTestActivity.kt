package com.cws.image

import android.content.res.ColorStateList
import android.databinding.DataBindingUtil
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatTextView
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import com.cws.image.databinding.ProgressIndicatorTestActivityBinding
import io.reactivex.disposables.Disposable
import timber.log.Timber
import kotlin.properties.Delegates

data class TimingValues(
  val timerDurationMilliseconds: Int,
  val elapsedTimeMilliseconds: Long
)

class ProgressIndicatorTestActivity : AppCompatActivity() {
  private val ANIMATION_START_TIME_MILLISECONDS = "animation-start-time-milliseconds"
  private lateinit var animationTest: ViewGroup
  private lateinit var image: ImageView
  private lateinit var countdown: AppCompatTextView
  private lateinit var circleCueTimer: CueTimer
  private lateinit var startAnimation: Button
  private var colorInitial: Int by Delegates.notNull<Int>()
  private var colorFinal: Int by Delegates.notNull<Int>()
  private var countdownSubscription: Disposable? = null
  private val lengthDuration = 8000L
  private val timerDurationMilliseconds = 8000
  private var viewModel = TimingValues(
    timerDurationMilliseconds = timerDurationMilliseconds,
    elapsedTimeMilliseconds = 0L
  )
  private var animationStartTimeMilliseconds: Long? = null

  private lateinit var binding: ProgressIndicatorTestActivityBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    //    setContentView(R.layout.progress_indicator_test_activity)
    binding = DataBindingUtil.setContentView<ProgressIndicatorTestActivityBinding>(
      this,
      R.layout.progress_indicator_test_activity
    )

    animationTest = findViewById(R.id.animation_test) as ViewGroup
    image = findViewById(R.id.bar_cue_timer) as ImageView
    countdown = findViewById(R.id.countdown) as AppCompatTextView
    circleCueTimer = findViewById(R.id.circle_cue_timer) as CueTimer
    startAnimation = findViewById(R.id.start_animation) as Button
    colorInitial = ContextCompat.getColor(this, R.color.timeToCueInitial)
    colorFinal = ContextCompat.getColor(this, R.color.timeToCueFinal)

    if (savedInstanceState != null) {
      val x = savedInstanceState.getLong(ANIMATION_START_TIME_MILLISECONDS, 0L)
      Timber.d(
        "Recovered ANIMATION_START_TIME_MILLISECONDS value from bundle: ${x}. Initing timer with timerDurationMilliseconds: ${timerDurationMilliseconds}, elapsedTimeMilliseconds: ${0L}.")
      if (x != 0L) {
        animationStartTimeMilliseconds = x
        val elapsedTimeMilliseconds = SystemClock.uptimeMillis() - x
        startAnimation.isEnabled = false

        Timber.d(
          "Recovered ANIMATION_START_TIME_MILLISECONDS value from bundle: ${x}. Initing timer with timerDurationMilliseconds: ${timerDurationMilliseconds}, elapsedTimeMilliseconds: ${elapsedTimeMilliseconds}.")
        viewModel = viewModel.copy(
          elapsedTimeMilliseconds = elapsedTimeMilliseconds)
      }
    }

    Timber.d("About to bind viewModel: ${viewModel}")
    binding.viewModel = viewModel
    Timber.d("Bound viewModel: ${viewModel}")

    circleCueTimer.hasCompleted()
      .subscribe { hasCompleted ->
        if (animationStartTimeMilliseconds != null && hasCompleted) {
          resumeAnimation()
        }
      }

//    if (savedInstanceState == null) {
//      Timber.d("There is no saved state. Initing timer with timerDurationMilliseconds: ${timerDurationMilliseconds}, elapsedTimeMilliseconds: ${0L}.")
//      circleCueTimer.init(timerDurationMilliseconds, 0L)
//    }
//    else {
//      val x = savedInstanceState.getLong(ANIMATION_START_TIME_MILLISECONDS, 0L)
//      Timber.d("Recovered ANIMATION_START_TIME_MILLISECONDS value from bundle: ${x}. Initing timer with timerDurationMilliseconds: ${timerDurationMilliseconds}, elapsedTimeMilliseconds: ${0L}.")
//      if (x == 0L) {
//        circleCueTimer.init(timerDurationMilliseconds, 0L)
//      }
//      else {
//        animationStartTimeMilliseconds = x
//        val elapsedTimeMilliseconds = SystemClock.uptimeMillis() - x
//        startAnimation.isEnabled = false
//
//        Timber.d("Recovered ANIMATION_START_TIME_MILLISECONDS value from bundle: ${x}. Initing timer with timerDurationMilliseconds: ${timerDurationMilliseconds}, elapsedTimeMilliseconds: ${elapsedTimeMilliseconds}.")
//        circleCueTimer.init(timerDurationMilliseconds, elapsedTimeMilliseconds)
//        if (!circleCueTimer.hasCompleted) {
//          resumeAnimation()
//        }
//      }
//    }
  }

  override fun onSaveInstanceState(outState: Bundle?) {
    super.onSaveInstanceState(outState)
//    Timber.d("outstate[\"ANIMATION_START_TIME_MILLISECONDS\"], before I modify it: ${outState?.getLong(ANIMATION_START_TIME_MILLISECONDS)}")
    Timber.d("animationStartTimeMilliseconds: ${animationStartTimeMilliseconds}")
    val x = animationStartTimeMilliseconds
    if (x != null) {
      Timber.d("Saving instance state at elapsed time ${SystemClock.uptimeMillis() - x}")
      outState?.putLong(ANIMATION_START_TIME_MILLISECONDS, x)
    }
  }

  fun startAnimation(view: View) {
    animationStartTimeMilliseconds = SystemClock.uptimeMillis()
    resumeAnimation()
    startAnimation.isEnabled = false
  }

  fun resumeAnimation() {
//    countdown.text = ""
//    image.animate()
//      .setDuration(8000L)
//      .setInterpolator(LinearInterpolator())
//      .translationX(-(image.width.toFloat() / 2))
//      .scaleX(0F)

//    val widthFinal = 0F
//
//    val lengthAnimator =
//      ObjectAnimator
//        .ofFloat(image, "scaleX", widthFinal)
//        .setDuration(lengthDuration)
//    lengthAnimator.interpolator = LinearInterpolator()
    //    lengthAnimator.start()

//    val colorSwitchPoint = 2000L
//    val colorDuration = 0L
//    val startDelay = lengthDuration - colorSwitchPoint - colorDuration
//    val colorAnimator =
//      ObjectAnimator
//        .ofInt(image.drawable, "color", colorInitial, colorFinal)
//        .setDuration(colorDuration)
//    colorAnimator.startDelay = startDelay
//    colorAnimator.setEvaluator(ArgbEvaluator())
//    colorAnimator.start()

    val angleAnimator = circleCueTimer.countdownAnimator
    angleAnimator.interpolator = LinearInterpolator()
//    angleAnimator.addListener(object : Animator.AnimatorListener {
//      override fun onAnimationEnd(animation: Animator?) {
//        Timber.d("About to set animationStartTimeMilliseconds to null")
//        animationStartTimeMilliseconds = null
//      }
//      override fun onAnimationRepeat(animation: Animator?) { }
//      override fun onAnimationCancel(animation: Animator?) { }
//      override fun onAnimationStart(animation: Animator?) { }
//    })
//      ObjectAnimator
//        .ofFloat(circleCueTimer, "needleAngleDegrees", -90F)
//        .setDuration(lengthDuration)

//    val animatorSet = AnimatorSet()
//    animatorSet.playTogether(lengthAnimator)
//    animatorSet.playTogether(lengthAnimator, colorAnimator)
//    animatorSet.playTogether(lengthAnimator, angleAnimator)
//    animatorSet.interpolator = LinearInterpolator()
//    animatorSet.addListener(object : Animator.AnimatorListener {
//      override fun onAnimationStart(animation: Animator?) {}
//
//      override fun onAnimationEnd(animation: Animator?) {
//        countdownSubscription?.dispose()
//      }
//
//      override fun onAnimationCancel(animation: Animator?) {}
//
//      override fun onAnimationRepeat(animation: Animator?) {}
//    })

//    animatorSet.start()
    angleAnimator.start()

//    val count = lengthDuration / 1000L
//    countdown.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryText))
//    countdown.text = count.toString()
//    countdownSubscription = Observable.intervalRange(1L, count, 1L, 1L, TimeUnit.SECONDS)
//      .observeOn(AndroidSchedulers.mainThread())
//      .subscribe(
//        { i -> countdown.text = (count - i).toString() },
//        {},
//        { countdown.text = "" }
//      )
  }

  fun reset(view: View) {
    countdownSubscription?.dispose()

    animationTest.removeView(image)

    animationTest = findViewById(R.id.animation_test) as ViewGroup
    binding = DataBindingUtil.setContentView<ProgressIndicatorTestActivityBinding>(
      this,
      R.layout.progress_indicator_test_activity
    )
    image = findViewById(R.id.bar_cue_timer) as ImageView
    countdown = findViewById(R.id.countdown) as AppCompatTextView
    circleCueTimer = findViewById(R.id.circle_cue_timer) as CueTimer
    startAnimation = findViewById(R.id.start_animation) as Button
    animationStartTimeMilliseconds = null
//    circleCueTimer.init(timerDurationMilliseconds, 0L)

    viewModel = TimingValues(
      timerDurationMilliseconds = timerDurationMilliseconds,
      elapsedTimeMilliseconds = 0L
    )
    binding.viewModel = viewModel

    (image.drawable as GradientDrawable).color = ColorStateList.valueOf(colorInitial)

    startAnimation.isEnabled = true
  }
}
