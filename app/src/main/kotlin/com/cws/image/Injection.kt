package com.cws.image

import android.content.Context
import android.content.SharedPreferences
import com.f2prateek.rx.preferences2.RxSharedPreferences
import com.github.andrewoma.dexx.kollection.immutableSetOf

fun provideLocalFileSystemInstructionsStorage(
  app: App
): LocalFileSystemInstructionsStorage {
  return LocalFileSystemInstructionsStorage(app as Context,
                                            app.storageDir,
                                            app.tokenFile)
}

val audioFileFormats = immutableSetOf(
  "aac",
  "flac",
  "mkv",
  "mp3",
  "mp4",
  "m4a",
  "ogg",
  "wav"
)

val iconFileFormats = immutableSetOf(
  "jpg",
  "png"
)

fun provideGetInstructions(app: App): GetInstructions {
  return GetInstructions.getInstance(
    provideLocalFileSystemInstructionsStorage(app),
    audioFileFormats,
    iconFileFormats)
}

fun provideSharedPreferences(context: Context): SharedPreferences {
  return context.getSharedPreferences(
    context.getString(R.string.shared_preferences_file_key),
    Context.MODE_PRIVATE)
}

class RxSharedPreferencesSingleton {
  companion object Factory {
    private var instance: RxSharedPreferences? = null
    fun getInstance(context: Context): RxSharedPreferences {
      val instanceSnapshot = instance

      if (instanceSnapshot == null) {
        val sharedPreferences = provideSharedPreferences(context)
        val newInstanceSnapshot = RxSharedPreferences.create(sharedPreferences)
        instance = newInstanceSnapshot
        return newInstanceSnapshot
      }
      return instanceSnapshot
    }
  }
}

fun provideRxSharedPreferences(context: Context): RxSharedPreferences {
  return RxSharedPreferencesSingleton.getInstance(context)
}

fun provideSharedPreferencesStorage(
  context: Context
): SharedPreferencesStorage {
  return SharedPreferencesStorage(context)
}

fun provideAuthentication(context: Context): Authentication {
  return Authentication.getInstance(
    provideSharedPreferencesStorage(context)
  )
}

fun provideKioskModeSetting(context: Context): KioskModeSetting {
  return KioskModeSetting.getInstance(
    provideSharedPreferencesStorage(context)
  )
}
