package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store

class Tick(val store: Store<State>,
           val instructionSequenceTimingHandler: Handler,
           val tickDuration: Long,
           val zeroTime: Long) : Runnable {

  override fun run() {
    try {
      store.dispatch(Action.Tick(tickDuration, SystemClock.uptimeMillis() - zeroTime))
    }
    finally {
      instructionSequenceTimingHandler.postDelayed(this, tickDuration)
    }
  }
}

class InstructionsSequenceMiddleware(val tickDuration: Long): Middleware<State> {
  private var mediaPlayer: MediaPlayer? = null
  private var instructionSequenceTimingThread: HandlerThread? = null
  private var instructionSequenceTimingHandler: Handler? = null
  private var isInstructionAudioPrepared: Boolean = false
  private var isInstructionGraphicsPrepared: Boolean = false
  private var isInstructionAudioFinished: Boolean = true
  private var isInstructionGraphicsFinished: Boolean = true
  private lateinit var tick: Tick

  fun onInstructionSequencePrepared(store: Store<State>) {
    println("isInstructionAudioPrepared: ${isInstructionAudioPrepared}, isInstructionGraphicsPrepared: ${isInstructionGraphicsPrepared}")
    if (isInstructionAudioPrepared && isInstructionGraphicsPrepared) {
      // 2016-10-02 Cort Spellman
      // TODO: Assertions on sequence of play, prep, start, finish, end?
      println("starting instruction sequence")
      val ht = HandlerThread("instructionSequenceTimingThread")
      ht.start()
      val h = Handler(ht.looper)

      tick = Tick(store = store,
                  instructionSequenceTimingHandler = h,
                  tickDuration = tickDuration,
                  zeroTime = SystemClock.uptimeMillis())

      instructionSequenceTimingThread = ht
      instructionSequenceTimingHandler =h
      h.post(tick)
      mediaPlayer?.start()
    } else {
      // 2016-09-26 Cort Spellman
      // TODO: Dispatch action: Display error to user, alert me to error.
    }
  }

  fun stopTicks() {
    instructionSequenceTimingThread?.quitSafely()
    instructionSequenceTimingThread = null
    instructionSequenceTimingHandler = null
  }

  fun onInstructionSequenceFinished(store: Store<State>) {
    if (isInstructionAudioFinished && isInstructionGraphicsFinished) {
      stopTicks()
      store.dispatch(Action.EndInstruction())
      store.dispatch(Action.NavigateBack())
    } else {
      // 2016-09-26 Cort Spellman
      // TODO: Dispatch action: Display error to user, alert me to error.
    }
  }

  override fun dispatch(store: Store<State>,
                        action: com.brianegan.bansa.Action,
                        next: NextDispatcher) {
    when (action) {
      is Action.PlayInstruction -> {
        isInstructionAudioFinished = false
        isInstructionGraphicsFinished = false

        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        // 2016-09-26 Cort Spellman
        // TODO: Wrap setDataSource in try, catch IllegalArgumentException, IOException.
        //       Dispatch action: Display error to user, alert me to error.
        mediaPlayer.setDataSource(action.instruction.path)

        mediaPlayer.setOnPreparedListener { mp ->
          isInstructionAudioPrepared = true
          store.dispatch(Action.SetInstructionTimings(mp.duration.toLong()))
          onInstructionSequencePrepared(store)
        }

        // 2016-09-26 Cort Spellman
        // TODO: This.
        mediaPlayer.setOnErrorListener { mp, what, extra ->
          false
        }

        mediaPlayer.setOnCompletionListener { mp ->
          isInstructionAudioFinished = true
          mp.release()
          this.mediaPlayer = null
          isInstructionAudioPrepared = false
          onInstructionSequenceFinished(store)
        }

        this.mediaPlayer = mediaPlayer
        mediaPlayer.prepareAsync()
        next.dispatch(action)

        isInstructionGraphicsPrepared = true
        onInstructionSequencePrepared(store)
      }

      is Action.Tick -> {
        // 2016-10-02 Cort Spellman
        // TODO: Set property values at end of graphics stuff:
//        isInstructionGraphicsFinished = true
//        isInstructionGraphicsPrepared = false
//        next.dispatch(action)
//        onInstructionSequenceFinished(store)
        next.dispatch(action)
      }

      is Action.AbortInstructionSequence -> {
        isInstructionAudioFinished = true
        isInstructionGraphicsFinished = true
        mediaPlayer?.release()
        mediaPlayer = null
        isInstructionAudioPrepared = false
        isInstructionGraphicsPrepared = false

        stopTicks()
        next.dispatch(action)
        store.dispatch(Action.EndInstruction())
      }

      else -> next.dispatch(action)
    }
  }
}
