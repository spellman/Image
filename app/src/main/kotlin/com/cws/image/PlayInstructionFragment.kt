package com.cws.image

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class MediaPlayerEvents {
  class AudiofocusGain : MediaPlayerEvents()
  class AudiofocusLoss : MediaPlayerEvents()
  class AudiofocusTransientLoss : MediaPlayerEvents()
  class Prepared : MediaPlayerEvents()
  class DelayInObtainingAudiofocus : MediaPlayerEvents()
}

sealed class VisualEvents {
  class TimeRemainingUpdate(val timeRemainingMilliseconds: Long) : VisualEvents()
}

class PlayInstructionFragment : BaseFragment() {
  private var mediaPlayer: MediaPlayer? = null
  private var hasBegunPreparingMediaPlayer = false
  private var hasSubscribedToTimerEvents = false
  val mediaPlayerEvents: BehaviorSubject<MediaPlayerEvents> =
    BehaviorSubject.create()
  val timerEvents: BehaviorSubject<VisualEvents> =
    BehaviorSubject.create()
  private lateinit var timerEventsSubscription : Disposable
  private val lostFocus: (Int) -> Boolean = { audioFocusChange -> audioFocusChange <= 0 }
  private val onAudioFocusChange: (Int) -> Unit = { audioFocusChange ->
    if (lostFocus(audioFocusChange)) {
      // 2017-02-11 Cort Spellman
      // TODO: This should not happen and is not acceptable.
      Log.e(this.javaClass.simpleName, "Lost audio focus, having requested and gained exclusive focus.")
    }
    when (audioFocusChange) {
      AudioManager.AUDIOFOCUS_LOSS -> {
        Log.d(this.javaClass.simpleName, "AudioManager.AUDIOFOCUS_LOSS")
        mediaPlayerEvents.onNext(MediaPlayerEvents.AudiofocusLoss())
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        Log.d(this.javaClass.simpleName, "AudioManager.AUDIOFOCUS_LOSS_TRANSIENT")
        mediaPlayerEvents.onNext(MediaPlayerEvents.AudiofocusTransientLoss())
      }

      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> {
        Log.d(this.javaClass.simpleName, "AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE")
        mediaPlayerEvents.onNext(MediaPlayerEvents.AudiofocusGain())
      }

      else -> {
        Log.d(this.javaClass.simpleName, "Unhandled audio focus change event with Int value: ${audioFocusChange}")
      }
    }
  }
  private val audioFocusChanges: BehaviorSubject<Int> = BehaviorSubject.create()
  private val onAudioFocusChangedCallBack =
    object : AudioManager.OnAudioFocusChangeListener {
      override fun onAudioFocusChange(audioFocusChange: Int) {
        audioFocusChanges.onNext(audioFocusChange)
      }
    }
  private lateinit var audiofocusChangesSubscription: Disposable
  private val audioManager by lazy {
    activity?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }
  private lateinit var timerSubscription: Disposable


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    retainInstance = true
    audiofocusChangesSubscription =
      audioFocusChanges.subscribe(onAudioFocusChange)
  }

  fun obtainAudioFocus(): Observable<String> {
    val audioFocusResult = audioManager.requestAudioFocus(
      onAudioFocusChangedCallBack,
      AudioManager.STREAM_MUSIC,
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)

    return Observable.create<String> { emitter ->
      if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        emitter.onNext("play instruction")
      }
      else {
        emitter.onError(
          Exception(
            "Failed to gain AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE audio focus."))
      }
    }
  }

  fun hasBegunPreparingInstruction(): Boolean {
    return hasBegunPreparingMediaPlayer
  }

  fun prepareInstructionAudio(audioFilePath: String) {
    hasBegunPreparingMediaPlayer = true
    mediaPlayer = MediaPlayer()
    mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
    mediaPlayer?.setOnPreparedListener { mp ->
      obtainAudioFocus()
        .retryWhen { errors ->
          errors
            .zipWith(Observable.range(4, 12),
                     BiFunction({ error: Throwable, i: Int -> i }))
            .flatMap { numberOfRetries ->
              // 2017-02-19 Cort Spellman
              // integral(2 ^ x, x, x = 4, x = 8, x <- Z) = 496
              // 496 milliseconds ~ a half-second delay
              if (numberOfRetries == 8) {
                mediaPlayerEvents.onNext(
                  MediaPlayerEvents.DelayInObtainingAudiofocus())
              }

              val durationUntilRetry = Math.pow(2.toDouble(), numberOfRetries.toDouble()).toLong()
              Log.d(this.javaClass.simpleName, "Unable to obtain audiofocus. Failure #${numberOfRetries}. Trying again in ${durationUntilRetry} milliseconds.")
              Observable.timer(durationUntilRetry, TimeUnit.MILLISECONDS) }
        }
        .subscribe(
          { audioFocusObtained ->
            mediaPlayerEvents.onNext(MediaPlayerEvents.Prepared())
          },
          { throwable ->
            Log.e(this.javaClass.simpleName, throwable.message)
            mediaPlayerEvents.onError(throwable)
          },
          { mediaPlayerEvents.onError(
            Exception(
              "Failed to gain AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE audio focus."))
          }
        )
    }
    mediaPlayer?.setOnErrorListener { mp, what, extra ->
      // 2016-11-23 Cort Spellman
      // TODO: This is too coarse - recover from errors as appropriate
      //       and tailor the message/log to the error:
      // See the possible values of what and extra at
      // https://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
      // Use onError for unrecoverable things? Or use onError for stuff that
      // is retryable?
      mediaPlayerEvents.onError(
        Exception(
          "Error preparing instruction audio. what: ${what}, extra: ${extra}"))
      true
    }
    mediaPlayer?.setOnCompletionListener { mp ->
      mediaPlayerEvents.onComplete()
    }

    try {
      mediaPlayer?.setDataSource(audioFilePath)
      mediaPlayer?.prepareAsync()
    }
    catch (e: IOException) {
      Log.e(this.javaClass.simpleName, e.message)
      mediaPlayerEvents.onError(e)
    }
    catch (e: IllegalArgumentException) {
      Log.e(this.javaClass.simpleName, e.message)
      mediaPlayerEvents.onError(e)
    }
  }

  fun startInstructionAudio() {
    mediaPlayer?.start()
  }

  fun stopInstructionAudio() {
    if (mediaPlayer?.isPlaying ?: false) {
      mediaPlayer?.stop()
    }
  }

  fun pauseInstruction() {
    if (mediaPlayer?.isPlaying ?: false) {
      mediaPlayer?.pause()
    }
  }

  fun millisecondsToNanoseconds(milliseconds: Long): Long {
    return milliseconds * 1000000L
  }

  fun nanoSecondsToMilliseconds(nanoseconds: Long): Long {
    return nanoseconds / 1000000L
  }

  fun startVisualTimeRemainingIndicator(
    cueStartTimeMilliseconds: Long,
    tickInterval: Long
  ) {
    if (!hasSubscribedToTimerEvents) {
      hasSubscribedToTimerEvents = true

      val cueStartTimeNanoseconds =
        System.nanoTime() + millisecondsToNanoseconds(cueStartTimeMilliseconds)
      val numberOfTicks = cueStartTimeMilliseconds / tickInterval

      Log.d(this.javaClass.simpleName, "About to subscribe to interval.")
      timerEventsSubscription = Observable.interval(tickInterval,
                                                    TimeUnit.MILLISECONDS)
        .map { tick ->
          nanoSecondsToMilliseconds(cueStartTimeNanoseconds - System.nanoTime())
        }
        .take(numberOfTicks)
        .subscribe(
          { timeRemainingMilliseconds ->
            timerEvents.onNext(
              VisualEvents.TimeRemainingUpdate(timeRemainingMilliseconds))
          },
          { e -> timerEvents.onError(e) },
          { timerEvents.onComplete() }
        )
    }
  }

  fun stopVisualTimeRemainingIndicator() {
    timerEventsSubscription.dispose()
  }

  override fun onDestroy() {
    Log.d(this.javaClass.simpleName, "Abandoning audio focus.")
    audioManager.abandonAudioFocus(onAudioFocusChangedCallBack)
    Log.d(this.javaClass.simpleName, "Unsubscribing from audio focus changes stream.")
    audiofocusChangesSubscription.dispose()
    timerEventsSubscription.dispose()

    if (mediaPlayer != null) {
      try {
        if (mediaPlayer?.isPlaying ?: false) {
          mediaPlayer?.stop()
        }
        mediaPlayer?.release()
      }
      finally {
        mediaPlayer = null
      }
    }
    super.onDestroy()
  }
}
