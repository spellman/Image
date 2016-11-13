package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store
import io.reactivex.subjects.PublishSubject
import java.io.IOException

class InstructionsSequenceMiddleware(val snackbarSubject: PublishSubject<SnackbarMessage>) : Middleware<State> {
  private var mediaPlayer: MediaPlayer? = null
  private val instructionSequenceTimingHandler: Handler = Handler()
  private var isInstructionAudioPrepared: Boolean = false
  private var isInstructionGraphicsPrepared: Boolean = false

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

  override fun dispatch(store: Store<State>,
                        action: com.brianegan.bansa.Action,
                        next: NextDispatcher) {
    when (action) {
      is Action.PlayInstruction -> {
        next.dispatch(action)

        val mp = MediaPlayer()
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC)
        mp.setOnPreparedListener { mp ->
          isInstructionAudioPrepared = true
          startInstructionSequence(store)
          store.dispatch(
            Action.SetInstructionTimings(action.instruction.cueStartTime,
                                         mp.duration.toLong()))
        }
        // 2016-09-26 Cort Spellman
        // TODO: Flesh this out.
        mp.setOnErrorListener { mp, what, extra ->
          false
        }
        mp.setOnCompletionListener { mp ->
          store.dispatch(Action.NavigateBack())
        }

        try {
          mp.setDataSource(action.instruction.absolutePath)
          mediaPlayer = mp
          mp.prepareAsync()
        }
        catch (e: IOException) {
          store.dispatch(
              Action.CouldNotPlayInstruction(action.instruction))
        }
        catch (e: IllegalArgumentException) {
          store.dispatch(
              Action.CouldNotPlayInstruction(action.instruction))
        }
      }

      is Action.CouldNotPlayInstruction -> {
        next.dispatch(action)
        store.dispatch(Action.NavigateBack())
        snackbarSubject.onNext(
          SnackbarMessage.CouldNotPlayInstruction(action.instruction.subject,
                                                  action.instruction.language,
                                                  action.instruction.absolutePath))
      }

      is Action.SetInstructionTimings -> {
        next.dispatch(action)
        isInstructionGraphicsPrepared = true
        startInstructionSequence(store)
      }

      is Action.NavigateBack -> {
        if (store.state.navigationStack.peek() is Scene.Instruction) {
          instructionSequenceTimingHandler.removeCallbacksAndMessages(null)

          mediaPlayer?.release()
          mediaPlayer = null
          isInstructionAudioPrepared = false
          isInstructionGraphicsPrepared = false

          store.dispatch(Action.EndInstruction())
        }

        next.dispatch(action)
      }

      else -> next.dispatch(action)
    }
  }
}
