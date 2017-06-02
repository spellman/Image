package com.cws.image

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.cws.image.databinding.InstructionVisualTestActivityBinding
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.run
import java.util.concurrent.TimeUnit

class InstructionVisualTestActivity : AppCompatActivity() {
  val timerDurationMilliseconds = 8000
  val viewModel = TimingValues(
    timerDurationMilliseconds = timerDurationMilliseconds,
    elapsedTimeMilliseconds = 0L
  )
  private lateinit var binding: InstructionVisualTestActivityBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = DataBindingUtil.setContentView<InstructionVisualTestActivityBinding>(
      this,
      R.layout.instruction_visual_test_activity
    )
    binding.viewModel = viewModel
  }

  fun animateGrowThenRestore(view: View, startAction: () -> Unit, endAction: () -> Unit): () -> Unit {
    val scale = 0.05F
    val halfDuration = 175L
    return {
      view.animate()
        .scaleXBy(scale)
        .scaleYBy(scale)
        .setDuration(halfDuration)
        .setStartDelay(0L)
        .setInterpolator(AccelerateDecelerateInterpolator())
        .withStartAction(startAction)
        .withEndAction {
          view.animate()
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

  fun play(view: View) {
    binding.play.isEnabled = false
    val timerAnimator = binding.cueTimer.countdownAnimator
    timerAnimator.interpolator = LinearInterpolator()
    timerAnimator.start()

    launch(CommonPool) {
      delay(timerDurationMilliseconds.toLong(), TimeUnit.MILLISECONDS)
      run(UI) {
        binding.cueText.animate()
          .alpha(1F)
          .setDuration(30L)
          .setInterpolator(LinearInterpolator())
          .withEndAction {
            animateGrowThenRestore(
              binding.cueText,
              startAction = {
                binding.cueTimer.animate()
                  .alpha(0.2F)
                  .setDuration(550L)
                  .setInterpolator(DecelerateInterpolator())
                  .withStartAction {
                    binding.cueHighlight.makeAnimator().start()
                  }
                  .start()
              },
              endAction = animateGrowThenRestore(
                binding.cueText,
                startAction = {},
                endAction = {
                launch(CommonPool) {
                  delay(1L, TimeUnit.SECONDS)
                  run(UI) {
                    binding = DataBindingUtil.setContentView<InstructionVisualTestActivityBinding>(
                      this@InstructionVisualTestActivity,
                      R.layout.instruction_visual_test_activity
                    )
                    binding.viewModel = viewModel
                    binding.play.isEnabled = true
                  }
                }
              }
              )
            ).invoke()
          }
          .start()
      }
    }
  }
}
