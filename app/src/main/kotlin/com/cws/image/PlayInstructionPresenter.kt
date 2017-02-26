package com.cws.image

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber

class PlayInstructionPresenter(
  private val activity: PlayInstructionActivity,
  private val mediaPlayerFragment: PlayInstructionFragment,
  private val instruction: InstructionViewModel
  ) {
  private val compositeDisposable = CompositeDisposable()
  private val cueStartTimeMilliseconds = instruction.cueStartTimeMilliseconds.toDouble()
  init {
    compositeDisposable.add(
      mediaPlayerFragment.mediaPlayerEvents
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
          { event ->
            val unused = when (event) {
              is MediaPlayerEvents.Prepared -> startInstruction()

              is MediaPlayerEvents.AudiofocusLoss -> {
                stopInstruction()
                activity.finishWithInstructionError(
                  instruction,
                  "Lost audiofocus, having requested and gained exclusive audiofocus.")
              }

              is MediaPlayerEvents.DelayInObtainingAudiofocus -> showDelayMessage()
            }
          },
          { e ->
            activity.finishWithInstructionError(instruction, e.message as String)
          },
          { activity.finishWithInstructionComplete() }
        ))

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
          { e -> Timber.e(e) },
          { showCue() }
        ))
  }

  fun playInstruction() {
    if (!isInstructionInitiated()) {
      prepareInstructionAudio()
    }
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
    activity.showDelayMessage("Preparing...")
  }

  fun showCue() {
    activity.showCue()
  }

  fun onDestroy() {
    compositeDisposable.dispose()
  }
}
