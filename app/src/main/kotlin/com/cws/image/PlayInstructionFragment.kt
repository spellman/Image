package com.cws.image

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import io.reactivex.subjects.BehaviorSubject
import java.io.IOException

sealed class MediaPlayerEvents {
  class Preparing : MediaPlayerEvents()
  class Prepared : MediaPlayerEvents()
}



class PlayInstructionFragment() : BaseFragment() {
  lateinit var presenter: PlayInstructionPresenter
  var mediaPlayer: MediaPlayer? = null
  val mediaPlayerEvents: BehaviorSubject<MediaPlayerEvents> =
    BehaviorSubject.create<MediaPlayerEvents>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    retainInstance = true
  }

  fun prepareInstructionAudio(audioFilePath: String) {
    mediaPlayerEvents.onNext(MediaPlayerEvents.Preparing())

    mediaPlayer = MediaPlayer()
    mediaPlayer?.setAudioStreamType(AudioManager.STREAM_MUSIC)
    mediaPlayer?.setOnPreparedListener { mp ->
      mediaPlayerEvents.onNext(MediaPlayerEvents.Prepared())
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

  override fun onDestroy() {
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
