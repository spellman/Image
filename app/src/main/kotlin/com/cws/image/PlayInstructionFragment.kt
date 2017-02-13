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
  class Preparing : MediaPlayerEvents()
}

class PlayInstructionFragment() : BaseFragment() {
  var mediaPlayer: MediaPlayer? = null
  val mediaPlayerEvents: BehaviorSubject<MediaPlayerEvents> =
    BehaviorSubject.create()

  val lostFocus: (Int) -> Boolean = { audioFocusChange -> audioFocusChange <= 0 }
  val onAudioFocusChange: (Int) -> Unit = { audioFocusChange ->
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
  val audioFocusChanges: BehaviorSubject<Int> = BehaviorSubject.create()
  val onAudioFocusChangedCallBack =
    object : AudioManager.OnAudioFocusChangeListener {
      override fun onAudioFocusChange(audioFocusChange: Int) {
        audioFocusChanges.onNext(audioFocusChange)
      }
    }
  lateinit var audioFocusChangeSubscription: Disposable
  val audioManager by lazy {
    activity?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    retainInstance = true
    audioFocusChangeSubscription =
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

  fun prepareInstructionAudio(audioFilePath: String) {
    mediaPlayerEvents.onNext(MediaPlayerEvents.Preparing())

    mediaPlayer = MediaPlayer()
    mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
    mediaPlayer?.setOnPreparedListener { mp ->
      obtainAudioFocus()
        .retryWhen { errors ->
          errors
            .zipWith(Observable.range(4, 12),
                     BiFunction({ error: Throwable, i: Int -> i }))
            .flatMap { numberOfRetries ->
              // 2017-02-11 Cort Spellman
              // TODO: If number of retries gets to 10 (2 ^ 10 = 1024 ms),
              // then show a snackbar saying, "Working" or something like that.
              Observable.timer(Math.pow(2.toDouble(),
                                        numberOfRetries.toDouble()).toLong(),
                               TimeUnit.MILLISECONDS) }
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

  override fun onDestroy() {
    Log.d(this.javaClass.simpleName, "Abandoning audio focus.")
    audioManager.abandonAudioFocus(onAudioFocusChangedCallBack)
    Log.d(this.javaClass.simpleName, "Unsubscribing from audio focus changes stream.")
    audioFocusChangeSubscription.dispose()

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
