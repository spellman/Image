package com.cws.image

import android.app.Activity
import android.content.Intent
import android.databinding.DataBindingUtil
import android.media.AudioManager
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.animation.LinearInterpolator
import com.cws.image.databinding.PlayInstructionActivityBinding
import com.jakewharton.rxbinding2.view.RxView
import io.reactivex.disposables.CompositeDisposable

class PlayInstructionViewModel(
  val subject: String,
  val language: String,
  val timerDurationMilliseconds: Int,
  val elapsedTimeMilliseconds: Long
) {
  val appVersionInfo = "Version ${BuildConfig.VERSION_NAME} | Version Code ${BuildConfig.VERSION_CODE} | Commit ${BuildConfig.GIT_SHA}"
}

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
      cueTimerHasInitialized = binding.cueTimer.hasInitialized.filter { x -> x }
    )
  }
  private val compositeDisposable = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setSupportActionBar(binding.toolbar)
    binding.viewModel = makeViewModel(mediaPlayerFragment.instructionEvents.value)

    compositeDisposable.add(
      RxView.attaches(binding.cueTimer).subscribe { _ ->
        presenter.notifyThatViewIsReady()
      }
    )

    volumeControlStream = AudioManager.STREAM_MUSIC
  }

  override fun onBackPressed() {
    presenter.stopInstruction()
    super.onBackPressed()
  }

  fun makeViewModel(event: InstructionEvent): PlayInstructionViewModel {
    val elapsedTimeMilliseconds = when (event) {
      is InstructionEvent.ReadyToPrepare -> 0L
      is InstructionEvent.AudioPreparing -> 0L
      is InstructionEvent.AudioPrepared -> 0L
      is InstructionEvent.InstructionStarted -> presenter.currentTime() - event.startTime
      is InstructionEvent.CueTimerFinished -> instruction.cueStartTimeMilliseconds
    }

    return PlayInstructionViewModel(
      subject = instruction.subject,
      language = instruction.language,
      timerDurationMilliseconds = instruction.cueStartTimeMilliseconds.toInt(),
      elapsedTimeMilliseconds = elapsedTimeMilliseconds
    )
  }

  // TODO: Factor out Snackbar stuff, as in MainActivity.
  // FIXME: What dismisses this indefinite-duration snackbar?
  fun showDelayMessage(message: String) {
    Snackbar.make(binding.root, message, Snackbar.LENGTH_INDEFINITE).show()
  }

  fun prepareCueTimer() {
    binding.cueTimer.countdownAnimator.interpolator = LinearInterpolator()
  }

  fun startCueTimer() {
    binding.cueTimer.countdownAnimator.start()
  }

  fun showCue() {
    binding.cueText.visibility = View.VISIBLE
  }

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
