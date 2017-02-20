package com.cws.image

import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class PlayInstructionPresenter(
  private val activity: PlayInstructionActivity,
  private val mediaPlayerFragment: PlayInstructionFragment,
  private val instruction: InstructionViewModel
  ) {
  private val compositeDisposable = CompositeDisposable()
  private val cueStartTimeMilliseconds = instruction.cueStartTimeMilliseconds.toDouble()
  init {
    Log.d(this.javaClass.simpleName, "Subscribing to mediaplayer events.")
    compositeDisposable.add(
      mediaPlayerFragment.mediaPlayerEvents
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { event ->
            val unused = when (event) {
              is MediaPlayerEvents.Prepared -> startInstruction()

              is MediaPlayerEvents.AudiofocusTransientLoss -> pauseInstruction()

              is MediaPlayerEvents.AudiofocusLoss -> stopInstruction()

              is MediaPlayerEvents.AudiofocusGain -> resumeInstruction()

              is MediaPlayerEvents.DelayInObtainingAudiofocus -> showDelayMessage()
            }
          },
          {
            throwable ->
            activity.finishWithInstructionError(instruction,
                                                throwable.message as String)
          },
          { activity.finishWithInstructionComplete() }
        ))

    Log.d(this.javaClass.simpleName, "Subscribing to timer events.")
    compositeDisposable.add(
      mediaPlayerFragment.timerEvents
        .subscribeOn(Schedulers.computation())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { event ->
            val unused = when (event) {
              is VisualEvents.TimeRemainingUpdate -> {
                setInstructionProgress(event.timeRemainingMilliseconds)
              }
            }
          },
          { e ->
            // 2017-02-18 Cort Spellman
            // This should never happen. What do you want to do if it does?
            Log.e(this.javaClass.simpleName, e.message)
          },
          { showCue() }
        ))
  }

  fun playInstruction() {
    if (!isInstructionInitiated()) {
      Log.d(this.javaClass.simpleName, "Instruction not already initiated. Initiating playing now.")
      prepareInstructionAudio()
    }
  }

  fun pauseInstruction() {
    mediaPlayerFragment.pauseInstruction()
  }

  fun resumeInstruction() {
    mediaPlayerFragment.startInstructionAudio()
  }

  fun stopInstruction() {
    mediaPlayerFragment.stopInstructionAudio()
    mediaPlayerFragment.stopVisualTimeRemainingIndicator()
  }

  fun isInstructionInitiated(): Boolean {
    return mediaPlayerFragment.hasBegunPreparingInstruction()
  }

  fun prepareInstructionAudio() {
    mediaPlayerFragment.prepareInstructionAudio(instruction.audioAbsolutePath)
  }

  fun startInstruction() {
    mediaPlayerFragment.startVisualTimeRemainingIndicator(
      cueStartTimeMilliseconds = instruction.cueStartTimeMilliseconds,
      tickInterval = (1000 / 16).toLong())
    mediaPlayerFragment.startInstructionAudio()
  }

  fun setInstructionProgress(timeRemainingMilliseconds: Long) {
    val percent = Math.max(
      (timeRemainingMilliseconds.toDouble() / cueStartTimeMilliseconds) * 100,
      0.0
    ).toInt()
    activity.setInstructionProgress(percent)
  }

  fun showDelayMessage() {
    activity.showDelayMessage("Working...")
  }

  fun showCue() {
    activity.showCue()
  }

  fun onDestroy() {
    Log.d(this.javaClass.simpleName, "Unsubscribing to mediaplayer events and timer events.")
    compositeDisposable.dispose()
  }
}
