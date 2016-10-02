package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.SystemClock
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store

class Tick(val store: Store<State>,
           val instructionSequenceTimingHandler: Handler,
           val tickDuration: Long) : Runnable {
  var zeroTime: Long = 0

  // 2016-10-02 Cort Spellman
  // LEFT OFF HERE
  // FIXME: I guess because this keeps making postDelayeds, handler#removeCallbacks
  // is not stopping the ticking. Rather, each Tick keeps ticking and every
  // time I start a sequence, another Tick is added to the handler.
  // Either find a way to remove the Tick or find another way to implement ticking.
  // Might need to extend HandlerThread.
  // Rx can do it if nothing else.
  override fun run() {
    store.dispatch(Action.Tick(tickDuration, SystemClock.uptimeMillis() - zeroTime))
    instructionSequenceTimingHandler.postDelayed(this, tickDuration)
  }
}

class InstructionsSequenceMiddleware(val instructionSequenceTimingHandler: Handler,
                                     val tickDuration: Long): Middleware<State> {
  var mediaPlayer: MediaPlayer? = null
  lateinit var tick: Tick

  override fun dispatch(store: Store<State>,
                        action: com.brianegan.bansa.Action,
                        next: NextDispatcher) {
    tick = Tick(store = store,
                instructionSequenceTimingHandler = instructionSequenceTimingHandler,
                tickDuration = tickDuration)

    when (action) {
      is Action.PlayInstruction -> {
        mediaPlayer = MediaPlayer()
        mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
        // 2016-09-26 Cort Spellman
        // TODO: Wrap setDataSource in try, catch IllegalArgumentException, IOException.
        //       Dispatch action: Display error to user, alert me to error.
        mediaPlayer?.setDataSource(action.instruction.path)

        mediaPlayer?.setOnPreparedListener { mediaPlayer ->
          store.dispatch(Action.InstructionAudioPrepared())
        }

        // 2016-09-26 Cort Spellman
        // TODO: This.
        mediaPlayer?.setOnErrorListener { mediaPlayer, what, extra ->
          false
        }

        mediaPlayer?.setOnCompletionListener { mediaPlayer ->
          store.dispatch(Action.InstructionAudioFinished())
        }

        mediaPlayer?.prepareAsync()
        next.dispatch(action)
        store.dispatch(Action.InstructionGraphicsPrepared())
      }

      is Action.InstructionAudioPrepared -> {
        next.dispatch(action)
        store.dispatch(Action.InstructionSequencePrepared())
      }

      is Action.InstructionGraphicsPrepared -> {
        next.dispatch(action)
        store.dispatch(Action.InstructionSequencePrepared())
      }

      is Action.InstructionSequencePrepared -> {
        if (store.state.isInstructionAudioPrepared &&
            store.state.isInstructionGraphicsPrepared) {
          next.dispatch(action)
          store.dispatch(Action.StartInstructionSequence())
        } else {
          // 2016-09-26 Cort Spellman
          // TODO: Dispatch action: Display error to user, alert me to error.
        }
      }

      is Action.StartInstructionSequence -> {
        tick.zeroTime = SystemClock.uptimeMillis()
        instructionSequenceTimingHandler.post(tick)
        next.dispatch(action)
        mediaPlayer?.start()
      }

      is Action.InstructionAudioFinished -> {
        mediaPlayer?.release()
        mediaPlayer = null
        next.dispatch(action)
        store.dispatch(Action.InstructionSequenceFinished())
      }

      is Action.InstructionGraphicsFinished -> {
        next.dispatch(action)
        store.dispatch(Action.InstructionSequenceFinished())
      }

      is Action.InstructionSequenceFinished -> {
        if (store.state.isInstructionAudioFinished &&
            store.state.isInstructionGraphicsFinished) {
          next.dispatch(action)
          store.dispatch(Action.EndInstructionSequence())
        } else {
          // 2016-09-26 Cort Spellman
          // TODO: Dispatch action: Display error to user, alert me to error.
        }
      }

      is Action.EndInstructionSequence -> {
        instructionSequenceTimingHandler.removeCallbacks(tick)
        next.dispatch(action)
        store.dispatch(Action.NavigateBack())
      }

      is Action.AbortInstructionSequence -> {
        mediaPlayer?.release()
        mediaPlayer = null
        instructionSequenceTimingHandler.removeCallbacks(tick)
        next.dispatch(action)
      }

      else -> next.dispatch(action)
    }
  }
}
