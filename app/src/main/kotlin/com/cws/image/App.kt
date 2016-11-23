package com.cws.image

import android.app.Application
import com.brianegan.bansa.BaseStore
import com.brianegan.bansa.Store
//import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import io.reactivex.subjects.PublishSubject
import trikita.anvil.Anvil

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

sealed class SnackbarMessage {
  class CouldNotPlayInstruction(val subject: String,
                                val language: String,
                                val absolutePath: String) : SnackbarMessage()
}

val idealCountDownDuration: Long = 5000L
val idealCueDuration: Long = 3000L

class App : Application() {
  lateinit var store: Store<State>
  val snackbarSubject: PublishSubject<SnackbarMessage> = PublishSubject.create()

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
    store = BaseStore(initialState,
                      reducer,
                      Logger("Image"),
                      ReadInstructionFiles(this),
                      PlayInstructionSequence(snackbarSubject))

    LeakCanary.install(this)
//    Stetho.initializeWithDefaults(this)
    store.subscribe { Anvil.render() }
  }
}
