package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.github.andrewoma.dexx.kollection.immutableSetOf

data class Interval(val lowerDelimiter: String,
                    val lowerEndPoint: Long,
                    val upperEndPoint: Long,
                    val upperDelimiter: String) {

  val lowerDelimiters = immutableSetOf("[", "(")
  val upperDelimiters = immutableSetOf("]", ")")

  init {
    assert(lowerEndPoint <= upperEndPoint)
    assert(lowerDelimiters.contains(lowerDelimiter))
    assert(upperDelimiters.contains(upperDelimiter))
  }

  fun contains(x: Long, error: Long): Boolean {
    val xPlusError = x + error
    val upperEndPointPlusError = upperEndPoint + error
    return when {
      (lowerDelimiter == "[" && upperDelimiter == "]") ->
        lowerEndPoint <= xPlusError && x <= upperEndPointPlusError

      (lowerDelimiter == "[" && upperDelimiter == ")") ->
        lowerEndPoint <= xPlusError && x < upperEndPointPlusError

      (lowerDelimiter == "(" && upperDelimiter == "]") ->
        lowerEndPoint < xPlusError && x <= upperEndPointPlusError

      (lowerDelimiter == "(" && upperDelimiter == ")") ->
        lowerEndPoint < xPlusError && x < upperEndPointPlusError

      else -> throw IllegalArgumentException("Unhandled interval delimiters. lowerDelimiter: ${lowerDelimiter}, upperDelimiter: ${upperDelimiter}")
    }
  }
}

fun isWithin(delta: Long, x1: Long, x2: Long): Boolean {
  return Interval("[", x1, x1, ")").contains(x2, delta)
}

fun isWithin(delta: Long, x1: Long): (x2: Long) -> Boolean {
  return { x2 -> isWithin(delta, x1, x2) }
}



class Tick(val store: Store<State>,
           val instructionSequenceTimingHandler: Handler,
           val tickDuration: Long,
           val zeroTime: Long) : Runnable {
  override fun run() {
    val start = SystemClock.uptimeMillis() - zeroTime
    try {
      store.dispatch(Action.Tick(tickDuration, start))
    }
    finally {
      instructionSequenceTimingHandler.postDelayed(this, tickDuration - (SystemClock.uptimeMillis() - zeroTime - start))
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

  fun startInstructionSequence(store: Store<State>) {
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
      instructionSequenceTimingHandler = h
      h.post(tick)
    }
  }

  fun stopTicks() {
    instructionSequenceTimingThread?.quitSafely()
    instructionSequenceTimingThread = null
    instructionSequenceTimingHandler = null
  }

  fun endInstructionSequence(store: Store<State>) {
    if (isInstructionAudioFinished && isInstructionGraphicsFinished) {
      stopTicks()
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

        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        // 2016-09-26 Cort Spellman
        // TODO: Wrap setDataSource in try, catch IllegalArgumentException, IOException.
        //       Dispatch action: Display error to user, alert me to error.
        mediaPlayer.setDataSource(action.instruction.path)

        mediaPlayer.setOnPreparedListener { mp ->
          isInstructionAudioPrepared = true
          startInstructionSequence(store)
          store.dispatch(Action.SetInstructionTimings(mp.duration.toLong()))
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
          endInstructionSequence(store)
        }

        this.mediaPlayer = mediaPlayer
        mediaPlayer.prepareAsync()
        next.dispatch(action)
      }

      is Action.SetInstructionTimings -> {
        next.dispatch(action)
        isInstructionGraphicsPrepared = true
        startInstructionSequence(store)
      }

      is Action.Tick -> {
//        // 2016-10-05 Cort Spellman
//        // TODO: Assertions on sequence of play, prep, start, finish, end?
        val time = action.time
        val timeIs = isWithin(action.tickDuration, time)
        val actions =
          immutableListOf(
              OnTickAction(
                  { t -> timeIs(0) },
                  { t, state -> run { mediaPlayer?.start() } },
                  "start media player"),

              OnTickAction(
                  { t -> timeIs(store.state.instructionAudioDuration) },
                  { t, state -> run {
                      isInstructionGraphicsFinished = true
                      isInstructionGraphicsPrepared = false
                      endInstructionSequence(store)
                    }
                  },
                  "end of audio - end instruction sequence")
          )

        actions.forEach { onTickAction: OnTickAction ->
                          if (onTickAction.pred(time)) {
                            onTickAction.action(time, store.state)
                          } }

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
