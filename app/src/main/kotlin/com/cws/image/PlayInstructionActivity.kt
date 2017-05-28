package com.cws.image

import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil
import android.media.AudioManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.cws.image.databinding.PlayInstructionActivityBinding
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber

enum class CueTextAlpha {
  VISIBLE,
  HIDDEN
}

fun CueTextAlpha.toInt(): Int {
  return when (this) {
    CueTextAlpha.HIDDEN -> 0
    CueTextAlpha.VISIBLE -> 1
  }
}

class PlayInstructionViewModel(
  val subject: String,
  val language: String,
  val timerDurationMilliseconds: Int,
  val elapsedTimeMilliseconds: Long,
  val cueTextAlpha: Int
)

class PlayInstructionActivity : AppCompatActivity() {
  companion object {
    fun startForResult(activity: Activity, requestCode: Int, instruction: InstructionViewModel) {
      val intent = Intent(activity, PlayInstructionActivity::class.java)
      intent.putExtra("instruction", instruction)
      activity.startActivityForResult(intent, requestCode)
    }
  }

  private val instruction by lazy {
    intent.getParcelableExtra<InstructionViewModel>("instruction")
  }
  private val binding: PlayInstructionActivityBinding by lazy {
    DataBindingUtil.setContentView<PlayInstructionActivityBinding>(
      this,
      R.layout.play_instruction_activity)
  }
  private val mediaPlayerFragment by lazy {
    val fragmentTag = PlayInstructionFragment::class.java.name
    val fm = supportFragmentManager
    fm.findFragmentByTag(fragmentTag) as? PlayInstructionFragment ?: let {
      val fragment = PlayInstructionFragment()
      fm.beginTransaction()
        .add(fragment, fragmentTag)
        .commit()
      fragment
    }
  }
  private val presenter by lazy {
    PlayInstructionPresenter(
      activity = this,
      mediaPlayerFragment = mediaPlayerFragment,
      instruction = instruction,
      cueTimerHasInitialized = binding.cueTimer.hasInitialized
        .doOnEach { _ -> Timber.d("CueTimer has initialized") }
    )
  }
  private val compositeDisposable = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setSupportActionBar(binding.toolbar)

    compositeDisposable.add(
      RxView.attaches(binding.cueTimer).subscribe { _ ->
        presenter.notifyThatViewIsAttached()
      }
    )

    volumeControlStream = AudioManager.STREAM_MUSIC
    requestKeepScreenOn(this)
  }

  override fun onBackPressed() {
    presenter.stopInstruction()
    super.onBackPressed()
  }

  fun setViewModel(viewModel: PlayInstructionViewModel) {
    binding.viewModel = viewModel
  }

  // TODO: Factor out Snackbar stuff, as in MainActivity.
  // FIXME: What dismisses this indefinite-duration snackbar?
  fun showDelayMessage(message: String) {
    Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).show()
  }

  fun prepareCueTimer() {
    Timber.d("About to set up CueTimer animator.")
    binding.cueTimer.countdownAnimator.interpolator = LinearInterpolator()
  }

  fun startCueTimer() {
    Timber.d("About to start CueTimer.")
    binding.cueTimer.countdownAnimator.start()
  }

  fun animateGrowThenRestore(view: View, startAction: () -> Unit = {}, endAction: () -> Unit = {}): () -> Unit {
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

  fun showCue() {
    binding.cueText.animate()
      .alpha(1F)
      .setDuration(30L)
      .setInterpolator(LinearInterpolator())
      .withEndAction {
        animateGrowThenRestore(
          view = binding.cueText,
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
          endAction = animateGrowThenRestore(binding.cueText)
        ).invoke()
      }
      .start()  }

  fun finishWithInstructionComplete() {
    setResult(Activity.RESULT_OK, Intent())
    finish()
  }

  fun finishWithInstructionError(instruction: InstructionViewModel, message: String) {
    setResult(
      Activity.RESULT_FIRST_USER,
      Intent().putExtra("instruction", instruction)
        .putExtra("message", message))
    finish()
  }

  override fun onDestroy() {
    compositeDisposable.dispose()
    presenter.onDestroy()
    super.onDestroy()
  }
}
