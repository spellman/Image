package com.cws.image

import android.app.Application
import android.os.Environment
import com.brianegan.bansa.BaseStore
import com.brianegan.bansa.Store
//import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import trikita.anvil.Anvil
import java.io.File

// Dummy data I started with. Keep for running in emulator.
//val languages = immutableSetOf("english",
//                               "spanish")
//
//val instructions =
//    immutableSetOf(
//        Instruction(subject = "chest",
//                    language = "english",
//                    path = "chest/english/path",
//                    cueStartTime = 1000),
//        Instruction(subject = "arm",
//                    language = "english",
//                    path = "arm/english/path",
//                    cueStartTime = 1000),
//        Instruction(subject = "chest",
//                    language = "spanish",
//                    path = "chest/spanish/path",
//                    cueStartTime = 1000),
//        Instruction(subject = "arm",
//                    language = "spanish",
//                    path = "arm/spanish/path",
//                    cueStartTime = 1000)
//    )

val idealCountDownDuration: Long = 5000L
val idealCueDuration: Long = 3000L

class App : Application() {
  lateinit var appDir: File
  lateinit var store: Store<State>

  // 2016-10-12 Cort Spellman
  // TODO: Use locales instead of string languages. The method of getting the
  // current locale depends on the Android API version of the device:
  // https://developer.android.com/reference/android/content/res/Configuration.html#locale
  // resources.configuration.locale is deprecated in API level 24 (7.0).
  // resources.configuration.locales() was added in 24 and
  // resources.configuration.locales().get(0) is the new way to get the primary
  // current locale.

  val initialState =
      State(
          isInitializing = true,
          navigationStack = NavigationStack(immutableListOf(Scene.Main())),
          needToRefreshInstructions = true,
          canReadInstructionFiles = false,
          canReadInstructionFilesMessage = "Initially assume instructions dir is not readable because it hasn't been checked for readability.",
          instructions = immutableSetOf(),
          languages = immutableSetOf(),
          language = "english", // Should be the language for the current system locale. (What if there are no instructions in the system language? Show a msg whenever there are no visible instructions, including then.),
          instructionToPlay = null,
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
    appDir = File(Environment.getExternalStorageDirectory(), packageName)
    store = BaseStore(initialState,
                      reducer,
                      Logger("Image"),
                      instructionFiles,
                      InstructionsSequenceMiddleware())

    LeakCanary.install(this)
//    Stetho.initializeWithDefaults(this)
    store.subscribe { Anvil.render() }
  }
}
