package com.cws.image

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class MediaPlayerEvents {
  class AudiofocusLoss : MediaPlayerEvents()
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
  private var timerEventsSubscription: Disposable? = null
  private val lostFocus: (Int) -> Boolean = { audioFocusChange -> audioFocusChange <= 0 }
  private val onAudioFocusChangedCallBack =
    object : AudioManager.OnAudioFocusChangeListener {
      override fun onAudioFocusChange(audioFocusChange: Int) {
        if (lostFocus(audioFocusChange)) {
          // 2017-02-11 Cort Spellman
          // I don't think this should happen, having requested and gained
          // exclusive audiofocus.
          Timber.e(
            Exception(
             "Lost audiofocus, having requested and gained exclusive audiofocus."))
          mediaPlayerEvents.onNext(MediaPlayerEvents.AudiofocusLoss())
        }
      }
    }
  private val audioManager by lazy {
    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }
  private val failedToGainAudiofocusException by lazy {
    Exception(
      "Failed to gain AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE audio focus."
    )
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    retainInstance = true
  }

  fun obtainAudioFocus(): Observable<String> {
    val audioFocusResult = audioManager.requestAudioFocus(
      onAudioFocusChangedCallBack,
      AudioManager.STREAM_MUSIC,
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)

    return Observable.create<String> { emitter ->
      if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        emitter.onNext("play-instruction")
      }
      else {
        emitter.onError(failedToGainAudiofocusException)
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

              // 2017-02-19 Cort Spellman: Exponential backoff.
              val durationUntilRetry =
                Math.pow(2.toDouble(), numberOfRetries.toDouble()).toLong()

              Timber.i("Unable to obtain audiofocus. Failure #${numberOfRetries}. Trying again in ${durationUntilRetry} milliseconds.")
              Observable.timer(durationUntilRetry, TimeUnit.MILLISECONDS) }
        }
        .subscribe(
          { audioFocusObtained ->
            mediaPlayerEvents.onNext(MediaPlayerEvents.Prepared())
          },
          { e ->
            Timber.e(e)
            mediaPlayerEvents.onError(e)
          },
          { mediaPlayerEvents.onError(failedToGainAudiofocusException) }
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
      Timber.e(e)
      mediaPlayerEvents.onError(e)
    }
    catch (e: IllegalArgumentException) {
      Timber.e(e)
      mediaPlayerEvents.onError(e)
    }
  }

  fun startInstructionAudio() {
    mediaPlayer?.start()
  }

  fun stopInstructionAudio() {
    mediaPlayer?.stop()
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
    timerEventsSubscription?.dispose()
  }

  override fun onDestroy() {
    audioManager.abandonAudioFocus(onAudioFocusChangedCallBack)
    timerEventsSubscription?.dispose()

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
