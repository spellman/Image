package com.cws.image

import android.app.Application
import android.os.Environment
import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import io.reactivex.subjects.PublishSubject
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
  val controllerMsgChan: PublishSubject<RequestModel> = PublishSubject.create()

  val getInstructionsChan: PublishSubject<RequestModel.GetInstructions> = PublishSubject.create()
  val instructionsResponseChan: PublishSubject<ResponseModel.Instructions> = PublishSubject.create()
  val setLanguageChan: PublishSubject<RequestModel.SetLanguage> = PublishSubject.create()
  val languageResponseChan: PublishSubject<ResponseModel.Language> = PublishSubject.create()
  val playInstructionChan: PublishSubject<RequestModel.PlayInstruction> = PublishSubject.create()
  val playInstructionResponseChan: PublishSubject<ResponseModel.InstructionToPlay> = PublishSubject.create()

  val updateChan: PublishSubject<ResponseModel> = PublishSubject.create()
  val presenterMsgChan: PublishSubject<PresenterMessage> = PublishSubject.create()

  val controller = Controller(controllerMsgChan)

  // 2016-01-12 Cort Spellman
  // Is it the app or the activity that creates the view model?
  // Well, do you want to use one activity or multiple activities?
  // I think the answer follows (it probably doesn't matter in the
  // single-activity case).
  val viewModel =
    ViewModel(
      appVersionInfo = "Version ${BuildConfig.VERSION_NAME} | Version Code ${BuildConfig.VERSION_CODE} | Commit ${BuildConfig.GIT_SHA}",
      instructionFilesReadFailureMessage = null,
      instructions = immutableSetOf(),
      instructionsForCurrentLanguage = mutableListOf(),
      unparsableInstructions = mutableListOf(),
      languages = mutableListOf(),
      language = null
    )

  val presenter = Presenter(this, viewModel, updateChan, presenterMsgChan)

  val storageDir by lazy {
    File(Environment.getExternalStorageDirectory(), packageName)
  }

  val tokenFile by lazy {
    File(storageDir, ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb")
  }

  val getInstructions by lazy {
    GetInstructions(
      EnsureInstructionsDirExistsAndIsAccessibleFromPC(storageDir, tokenFile, this),
      FileSystemGetInstructionsGateway(storageDir, immutableSetOf(tokenFile)),
      getInstructionsChan,
      instructionsResponseChan)
  }

  val setLanguage = SetLanguage(setLanguageChan, languageResponseChan)

  // 2017-01-22 Cort Spellman
  // TODO: getInstructionsChan and instructionsResponseChan should not be here in the
  // top level. Set up that plumbing in an init block for Update -- it's all between
  // update and its interactors.
  // In the top level, here, I should just be hooking up the main components --
  // controller to update to presenter.
  // IF I find that update is doing nothing but dispatching, maybe I'll want to
  // get rid of it and directly wire the controller to the interactors.
  // But I may do common stuff -- I'll want to log things and send them to a
  // server, for instance.
  val update by lazy {
    Update(
      getInstructions,
      setLanguage,
      controllerMsgChan,
      updateChan)
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

    controller.start()
    presenter.start()
    getInstructions.start()
    setLanguage.start()
    update.start()

    LeakCanary.install(this)
    Stetho.initializeWithDefaults(this)

    // Init
    controller.getInstructions()
  }
}
