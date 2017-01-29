package com.cws.image

import android.app.Application
import android.os.Environment
import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import java.io.File

// Dummy data I started with. Keep for running in emulator.
//val languages = immutableSetOf("english",
//                               "spanish")
//
//val parsedInstructions =
//    immutableSetOf(
//        Instruction(subject = "chest",
//                    language = "english",
//                    absolutePath = "chest/english/absolutePath",
//                    cueStartTime = 1000),
//        Instruction(subject = "arm",
//                    language = "english",
//                    absolutePath = "arm/english/absolutePath",
//                    cueStartTime = 1000),
//        Instruction(subject = "chest",
//                    language = "spanish",
//                    absolutePath = "chest/spanish/absolutePath",
//                    cueStartTime = 1000),
//        Instruction(subject = "arm",
//                    language = "spanish",
//                    absolutePath = "arm/spanish/absolutePath",
//                    cueStartTime = 1000)
//    )

sealed class PresenterMessage {
  class LanguageChanged : PresenterMessage() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }
  class InstructionsChanged : PresenterMessage() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  sealed class SnackbarMessage : PresenterMessage() {
    class CouldNotReadInstructions(
      val message: String
    ) : SnackbarMessage() {
      override fun toString(): String {
        return """${this.javaClass.canonicalName}:
               |message: ${message}""".trimMargin()
      }
    }

    class CouldNotPlayInstruction(
      val subject: String,
      val language: String,
      val absolutePath: String
    ) : SnackbarMessage() {
      override fun toString(): String {
        return """${this.javaClass.canonicalName}:
               |subject: ${subject}
               |language: ${language}
               |absolutePath: ${absolutePath}""".trimMargin()
      }
    }
  }
}

val idealCountDownDuration: Long = 5000L
val idealCueDuration: Long = 3000L

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

  // 2016-10-12 Cort Spellman
  // TODO: Use locales instead of string languages. The method of getting the
  // current locale depends on the Android API version of the device:
  // https://developer.android.com/reference/android/content/res/Configuration.html#locale
  // resources.configuration.locale is deprecated in API level 24 (7.0).
  // resources.configuration.locales() was added in 24 and
  // resources.configuration.locales().get(0) is the new way to get the primary
  // current locale.

  val initialState =
    BansaState(
      isInitializing = true,
      navigationStack = NavigationStack(immutableListOf(Scene.Main())),
      needToRefreshInstructions = true,
      canReadInstructionFiles = false,
      canReadInstructionFilesMessage = "Initially assume parsedInstructions dir is not readable because it hasn't been checked for readability.",
      instructions = immutableSetOf(),
      unparsableInstructions = immutableSetOf(),
      languages = immutableSetOf(),
      language = "english", // Should be the language for the current system locale. (What if there are no parsedInstructions in the system language? Show a msg whenever there are no visible parsedInstructions, including then.),
      instructionLoadingMessage = null,
      countDownStartTime = 0,
      countDownDuration = 0,
      countDownValue = null,
      cueStartTime = 0,
      cueStopTime = 0,
      instructionAudioDuration = 0,
      subjectToDisplay = null,
      languageToDisplay = null,
      cueMessage = null
    )

  override fun onCreate() {
    super.onCreate()

    LeakCanary.install(this)
    Stetho.initializeWithDefaults(this)
  }
}
