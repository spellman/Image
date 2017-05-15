package com.cws.image

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class InstructionEvent {
  class ReadyToPrepare : InstructionEvent()
  class AudioPreparing : InstructionEvent()
  class AudioPrepared : InstructionEvent()
  data class InstructionStarted(val startTime: Long) : InstructionEvent()
  class CueTimerFinished : InstructionEvent()
}

sealed class AudioFocusEvent {
  class DelayInObtainingAudiofocus : AudioFocusEvent()
  class AudiofocusLoss : AudioFocusEvent()
}

class PlayInstructionFragment : BaseFragment() {
  private var mediaPlayer: MediaPlayer? = null
  val instructionEvents: BehaviorSubject<InstructionEvent> =
    BehaviorSubject.createDefault(InstructionEvent.ReadyToPrepare())
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
          audioFocusEvents.onNext(AudioFocusEvent.AudiofocusLoss())
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
  val audioFocusEvents: PublishSubject<AudioFocusEvent> = PublishSubject.create()
  val compositeDisposable = CompositeDisposable()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    retainInstance = true
  }

  fun obtainAudioFocus(): Observable<Unit> {
    val audioFocusResult = audioManager.requestAudioFocus(
      onAudioFocusChangedCallBack,
      AudioManager.STREAM_MUSIC,
      AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)

    return Observable.create<Unit> { emitter ->
      if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
        emitter.onNext(Unit)
      }
      else {
        emitter.onError(failedToGainAudiofocusException)
      }
    }
  }

  fun prepareInstructionAudio(audioFilePath: String) {
    instructionEvents.onNext(InstructionEvent.AudioPreparing())
    mediaPlayer = MediaPlayer()
    mediaPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
    mediaPlayer!!.setOnPreparedListener { _ ->
      obtainAudioFocus()
        .retryWhen { errors ->
          errors
            .zipWith(Observable.range(4, 12),
                     BiFunction { error: Throwable, i: Int -> i })
            .flatMap { numberOfRetries ->
              // 2017-02-19 Cort Spellman
              // integral(2 ^ x, x, x = 4, x = 8, x <- Z) = 496
              // 496 milliseconds ~ a half-second delay
              if (numberOfRetries == 8) {
                audioFocusEvents.onNext(
                  AudioFocusEvent.DelayInObtainingAudiofocus()
                )
              }

              // 2017-02-19 Cort Spellman: Exponential backoff.
              val durationUntilRetry =
                Math.pow(2.toDouble(), numberOfRetries.toDouble()).toLong()

              Timber.i("Unable to obtain audiofocus. Failure #${numberOfRetries}. Trying again in ${durationUntilRetry} milliseconds.")
              Observable.timer(durationUntilRetry, TimeUnit.MILLISECONDS) }
        }
        .subscribe(
          { audioFocusObtained ->
            instructionEvents.onNext(InstructionEvent.AudioPrepared())
          },
          { e ->
            Timber.e(e)
            audioFocusEvents.onError(e)
          },
          { audioFocusEvents.onError(failedToGainAudiofocusException) }
        )
    }
    mediaPlayer!!.setOnErrorListener { mp, what, extra ->
      // 2016-11-23 Cort Spellman
      // TODO: This is too coarse - recover from errors as appropriate
      //       and tailor the message/log to the error:
      // See the possible values of what and extra at
      // https://developer.android.com/reference/android/media/MediaPlayer.OnErrorListener.html
      // Use onError for unrecoverable things? Or use onError for stuff that
      // is retryable?
      instructionEvents.onError(
        Exception(
          "Error preparing instruction audio. what: ${what}, extra: ${extra}"))
      true
    }
    mediaPlayer!!.setOnCompletionListener { _ -> instructionEvents.onComplete() }

    try {
      mediaPlayer!!.setDataSource(audioFilePath)
      mediaPlayer!!.prepareAsync()
    }
    catch (e: IOException) {
      Timber.e(e)
      instructionEvents.onError(e)
    }
    catch (e: IllegalArgumentException) {
      Timber.e(e)
      instructionEvents.onError(e)
    }
  }

  suspend fun startInstruction(instructionDuration: Long, startTime: Long) {
    compositeDisposable.add(
      Observable.timer(instructionDuration, TimeUnit.MILLISECONDS)
        .subscribe { _ ->
          instructionEvents.onNext(InstructionEvent.CueTimerFinished())
        }
    )
    mediaPlayer!!.start()
    instructionEvents.onNext(InstructionEvent.InstructionStarted(startTime))
  }

  fun stopInstructionAudio() {
    mediaPlayer!!.stop()
  }

  override fun onDestroy() {
    compositeDisposable.dispose()
    audioManager.abandonAudioFocus(onAudioFocusChangedCallBack)

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
