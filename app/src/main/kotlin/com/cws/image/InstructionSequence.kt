package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import com.brianegan.bansa.Middleware

val instructionSequence = Middleware<State> { store, action, next ->
  when (action) {
    is Action.PlayInstruction -> {
      val mp = MediaPlayer()
      mp.setAudioStreamType(AudioManager.STREAM_MUSIC)

      // 2016-09-26 Cort Spellman
      // TODO: Wrap setDataSource in try, catch IllegalArgumentException,
      // IOException.
      // Catch: Display message. Dispatch action.
      mp.setDataSource(action.instruction.path)

      mp.setOnPreparedListener { mediaPlayer ->
        store.dispatch(Action.InstructionAudioPrepared(mediaPlayer))
      }

      // 2016-09-26 Cort Spellman
      // TODO: This.
      mp.setOnErrorListener { mediaPlayer, what, extra ->
        false
      }

      mp.setOnCompletionListener { mediaPlayer ->
        store.dispatch(Action.InstructionAudioFinished())
      }

      mp.prepareAsync()

      next.dispatch(action)
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
        val mp = store.state.mediaPlayer
        if (mp is MediaPlayer) {
          next.dispatch(action)
          // 2016-09-26 Cort Spellman
          // TODO: Make a timer emit ticks.
          // In each tick, dispatch Action.Tick.
          mp.start()
        }
        else {
          // 2016-09-26 Cort Spellman
          // TODO: Dispatch action: Display error to user, alert me to error.
        }
      }
    }

    is Action.InstructionAudioFinished -> {
      store.state.mediaPlayer?.release()
      next.dispatch(action)
    }

    is Action.InstructionSequenceFinished -> {
      next.dispatch(action)
      store.dispatch(Action.NavigateBack())
    }

    else -> next.dispatch(action)
  }
}
