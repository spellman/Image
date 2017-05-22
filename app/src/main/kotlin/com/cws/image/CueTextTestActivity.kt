package com.cws.image

import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.CycleInterpolator
import com.cws.image.databinding.CueTextTestActivityBinding
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.util.concurrent.TimeUnit

class CueTextTestActivity : AppCompatActivity() {
  private val scaledDensity by lazy {
    this.resources.displayMetrics.scaledDensity
  }
  private val originalTextSize: Float by lazy {
    binding.cueText.textSize / scaledDensity
  }
  private val enlargedTextSize: Float by lazy {
    originalTextSize * 1.05F
  }

  private val binding: CueTextTestActivityBinding by lazy {
    DataBindingUtil.setContentView<CueTextTestActivityBinding>(
      this,
      R.layout.cue_text_test_activity
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding
  }

  fun cycle(endAction: () -> Unit): () -> Unit {
    val scale = 0.05F
    return {
      binding.cueText.animate()
        .scaleXBy(scale)
        .scaleYBy(scale)
        .setDuration(400L)
        .setStartDelay(0L)
        .setInterpolator(CycleInterpolator(0.5F))
        .withEndAction(endAction)
        .start()
    }
  }

  fun accelDecelCycle(startAction: () -> Unit, endAction: () -> Unit): () -> Unit {
    val scale = 0.05F
    val halfDuration = 200L
    return {
      binding.cueText.animate()
        .scaleXBy(scale)
        .scaleYBy(scale)
        .setDuration(halfDuration)
        .setStartDelay(0L)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .withStartAction(startAction)
        .withEndAction {
          binding.cueText.animate()
            .scaleXBy(-scale)
            .scaleYBy(-scale)
            .setDuration(halfDuration)
            .setStartDelay(0L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction(endAction)
            .start()
        }
        .start()
    }
  }

  fun pause(duration: Long, timeUnit: TimeUnit): (() -> Unit) -> () -> Unit {
    return { endAction ->
      {
        runBlocking { delay(duration, timeUnit) }
        endAction.invoke()
      }
    }
  }

  val halfDuration = 200L

  fun increaseTextSize(interpolator: TimeInterpolator): ObjectAnimator {
    val x = ObjectAnimator.ofFloat(
      binding.cueText, "textSize", originalTextSize, enlargedTextSize
    )
    x.duration = halfDuration
    x.interpolator = interpolator
    return x
  }

  fun decreaseTextSize(interpolator: TimeInterpolator): ObjectAnimator {
    val x = ObjectAnimator.ofFloat(
      binding.cueText, "textSize", enlargedTextSize, originalTextSize
    )
    x.duration = halfDuration
    x.interpolator = interpolator
    return x
  }

  fun play(view: View) {
    //    val interpolator = AccelerateDecelerateInterpolator()
    //
    //    val animatorSet = AnimatorSet()
    //    animatorSet.playSequentially(
    //      increaseTextSize(interpolator),
    //      decreaseTextSize(interpolator),
    //      increaseTextSize(interpolator),
    //      decreaseTextSize(interpolator)
    //    )
    //    animatorSet.start()
    //    animateGrowThenRestore(
    //      {
    //        //        (binding.cueTextContainer.background as TransitionDrawable).startTransition(halfDuration.toInt())
    //        binding.cueHighlight.animate()
    //          .scaleX(2.5F)
    //          .scaleY(2.5F)
    //          .alpha(0.3F)
    //          .setDuration(1000L)
    //          .setInterpolator(DecelerateInterpolator())
    //          .withEndAction {
    //            binding.cueHighlight.animate()
    //              .scaleX(2.625F)
    //              .scaleY(2.625F)
    //              .alpha(0F)
    //              .setDuration(500L)
    //              .setInterpolator(LinearInterpolator())
    //              .start()
    //          }
    //          .start()
    //      },
    //      animateGrowThenRestore({}, {})).invoke()
    accelDecelCycle(
      { binding.cueHighlight.makeAnimator().start() },
      accelDecelCycle({}, {})).invoke()
//    binding.cueHighlight.makeAnimator().start()
  }
}
