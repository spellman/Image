package com.cws.image

import android.app.Application
import android.os.Environment
import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import io.reactivex.subjects.PublishSubject
import java.io.File

class App : Application() {
  val storageDir by lazy {
    File(Environment.getExternalStorageDirectory(), packageName)
  }

  val tokenFile by lazy {
    File(storageDir, ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb")
  }

  val ensureInstructionsDir by lazy {
    EnsureInstructionsDirExistsAndIsAccessibleFromPC(storageDir, tokenFile, this)
  }

  val getInstructionsGateway by lazy {
    FileSystemGetInstructionsGateway(storageDir, immutableSetOf(tokenFile))
  }

  val viewModel by lazy {
    ViewModel(
      app = this,
      msgChan = PublishSubject.create<ViewModelMessage>(),
      needToRefreshInstructions = true,
      instructionFilesReadFailureMessage = null,
      instructions = immutableSetOf(),
      instructionsForCurrentLanguage = immutableSetOf(),
      unparsableInstructions = immutableSetOf(),
      languages = immutableSetOf(),
      language = null,
      mediaPlayer = null,
      selectedInstruction = null
      )
  }

  // 2016-10-12 Cort Spellman
  // TODO: Use locales instead of string languages. The method of getting the
  // current locale depends on the Android API version of the device:
  // https://developer.android.com/reference/android/content/res/Configuration.html#locale
  // resources.configuration.locale is deprecated in API level 24 (7.0).
  // resources.configuration.locales() was added in 24 and
  // resources.configuration.locales().get(0) is the new way to get the primary
  // current locale.

  override fun onCreate() {
    super.onCreate()
    LeakCanary.install(this)
    Stetho.initializeWithDefaults(this)
  }
}
