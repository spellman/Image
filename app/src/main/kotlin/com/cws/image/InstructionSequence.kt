package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store

class InstructionsSequenceMiddleware : Middleware<State> {
  private var mediaPlayer: MediaPlayer? = null
  private val instructionSequenceTimingHandler: Handler = Handler()
  private var isInstructionAudioPrepared: Boolean = false
  private var isInstructionGraphicsPrepared: Boolean = false
  private var isInstructionAudioFinished: Boolean = true
  private var isInstructionGraphicsFinished: Boolean = true

  fun startInstructionSequence(store: Store<State>) {
    println("isInstructionAudioPrepared: ${isInstructionAudioPrepared}, isInstructionGraphicsPrepared: ${isInstructionGraphicsPrepared}")
    if (isInstructionAudioPrepared && isInstructionGraphicsPrepared) {
      // 2016-10-02 Cort Spellman
      // TODO: Assertions on sequence of play, prep, start, finish, end?
      println("starting instruction sequence")

      store.dispatch(Action.ClearInstructionLoadingMessage())

      mediaPlayer?.start()

      val countDownStartTime = store.state.countDownStartTime
      val countDownDuration = store.state.countDownDuration
      for (countDownValue in countDownDuration / 1000 downTo 1 step 1) {
        instructionSequenceTimingHandler.postDelayed(
            { store.dispatch(Action.SetCountDownValue(countDownValue)) },
            countDownStartTime + countDownDuration - (countDownValue * 1000))
      }

      instructionSequenceTimingHandler.postDelayed(
          {
            store.dispatch(Action.SetCueMessage("Take the image now."))
          },
          store.state.cueStartTime)

      instructionSequenceTimingHandler.postDelayed(
          { store.dispatch(Action.ClearCueMessage()) },
          store.state.cueStopTime)
    }
  }

  fun endInstructionSequence(store: Store<State>) {
    if (isInstructionAudioFinished && isInstructionGraphicsFinished) {
      store.dispatch(Action.EndInstruction())
      store.dispatch(Action.NavigateBack())
    }
  }

  override fun dispatch(store: Store<State>,
                        action: com.brianegan.bansa.Action,
                        next: NextDispatcher) {
    when (action) {
      is Action.PlayInstruction -> {
        isInstructionAudioFinished = false
        isInstructionGraphicsFinished = false

        val mp = MediaPlayer()
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
        // 2016-09-26 Cort Spellman
        // TODO: Wrap setDataSource in try, catch IllegalArgumentException, IOException.
        //       Dispatch action: Display error to user, alert me to error.
        mp.setDataSource(action.instruction.path)

        mp.setOnPreparedListener { mp ->
          isInstructionAudioPrepared = true
          startInstructionSequence(store)
          store.dispatch(Action.SetInstructionTimings(mp.duration.toLong()))
        }

        // 2016-09-26 Cort Spellman
        // TODO: This.
        mp.setOnErrorListener { mp, what, extra ->
          false
        }

        mp.setOnCompletionListener { mp ->
          isInstructionAudioFinished = true
          mp.release()
          mediaPlayer = null
          isInstructionAudioPrepared = false
          isInstructionGraphicsFinished = true
          isInstructionGraphicsPrepared = false
          endInstructionSequence(store)
        }

        mediaPlayer = mp
        mp.prepareAsync()
        next.dispatch(action)
      }

      is Action.SetInstructionTimings -> {
        next.dispatch(action)
        isInstructionGraphicsPrepared = true
        startInstructionSequence(store)
      }

      is Action.AbortInstructionSequence -> {
        isInstructionAudioFinished = true
        isInstructionGraphicsFinished = true
        mediaPlayer?.release()
        mediaPlayer = null
        isInstructionAudioPrepared = false
        isInstructionGraphicsPrepared = false

        next.dispatch(action)
        store.dispatch(Action.EndInstruction())
      }

      is Action.EndInstruction -> {
        instructionSequenceTimingHandler.removeCallbacksAndMessages(null)
        next.dispatch(action)
      }

      else -> next.dispatch(action)
    }
  }
}
