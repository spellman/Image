package com.cws.image

import android.util.Log
import io.reactivex.disposables.Disposable

class PlayInstructionPresenter(
  private val activity: PlayInstructionActivity,
  private val mediaPlayerFragment: PlayInstructionFragment,
  private val instruction: Instruction
  ) {
  private val audioSubscription: Disposable
  init {
    Log.d(this.javaClass.simpleName, "Subscribing to mediaplayer events.")
    audioSubscription = mediaPlayerFragment.mediaPlayerEvents.subscribe(
      { event ->
        val unused = when (event) {
          is MediaPlayerEvents.Preparing ->
            Log.d(this.javaClass.simpleName, "Note that media player is preparing.")

          is MediaPlayerEvents.Prepared -> startInstructionAudio()

          is MediaPlayerEvents.AudiofocusTransientLoss -> pauseInstruction()

          is MediaPlayerEvents.AudiofocusLoss -> stopInstruction()

          is MediaPlayerEvents.AudiofocusGain -> resumeInstruction()
        }
      },
      {
        throwable ->
        activity.finishWithInstructionError(instruction,
                                            throwable.message as String)
      },
      { activity.finishWithInstructionComplete() }
    )
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
  }

  fun isInstructionInitiated(): Boolean {
    return mediaPlayerFragment.mediaPlayerEvents.hasValue()
    || mediaPlayerFragment.mediaPlayerEvents.hasComplete()
  }

  fun prepareInstructionAudio() {
    mediaPlayerFragment.prepareInstructionAudio(instruction.absolutePath)
  }

  fun startInstructionAudio() {
    mediaPlayerFragment.startInstructionAudio()
  }

  fun onDestroy() {
    Log.d(this.javaClass.simpleName, "Unsubscribing to mediaplayer events.")
    audioSubscription.dispose()
  }
}
