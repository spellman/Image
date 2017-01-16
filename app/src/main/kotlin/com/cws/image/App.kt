package com.cws.image

import android.app.Application
import android.os.Environment
//import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import io.reactivex.processors.FlowableProcessor
import io.reactivex.processors.UnicastProcessor
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

sealed class SnackbarMessage {
  class CouldNotPlayInstruction(
    val subject: String,
    val language: String,
    val absolutePath: String
  ) : SnackbarMessage()
}

val idealCountDownDuration: Long = 5000L
val idealCueDuration: Long = 3000L

class App : Application() {
  val msgChan: UnicastProcessor<RequestModel> = UnicastProcessor.create()

  val ensureInstructionsDirChan: UnicastProcessor<RequestModel.EnsureInstructionsDirExistsAndIsAccessibleFromPC> = UnicastProcessor.create()
  val instructionsDirResponseChan: UnicastProcessor<ResponseModel.InstructionsDirResponse> = UnicastProcessor.create()
  val getInstructionsChan: UnicastProcessor<RequestModel.GetInstructions> = UnicastProcessor.create()
  val instructionsResponseChan: UnicastProcessor<ResponseModel.Instructions> = UnicastProcessor.create()
  val playInstructionChan: UnicastProcessor<RequestModel.PlayInstruction> = UnicastProcessor.create()
  val playInstructionResponseChan: UnicastProcessor<ResponseModel.InstructionToPlay> = UnicastProcessor.create()

  val updateChan: UnicastProcessor<ResponseModel> = UnicastProcessor.create()

  val controller = Controller(msgChan)
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
      unparsableInstructions = immutableSetOf(),
      languages = immutableSetOf(),
      language = "english"
    )

  val presenter = Presenter(viewModel, controller, updateChan)

  val storageDir by lazy {
    File(Environment.getExternalStorageDirectory(), packageName)
  }

  val tokenFile by lazy {
    File(storageDir, ".tokenFileToMakeDirAppearWhenDeviceIsMountedViaUsb")
  }

  val ensureInstructionsDirExistsAndIsAccessibleFromPC by lazy {
    EnsureInstructionsDirExistsAndIsAccessibleFromPC(
      storageDir,
      tokenFile,
      this,
      ensureInstructionsDirChan,
      instructionsDirResponseChan)
  }

  val getInstructions by lazy {
    GetInstructions(
      FileSystemGetInstructionsGateway(storageDir, immutableSetOf(tokenFile)),
      getInstructionsChan,
      instructionsResponseChan)
  }

  val update by lazy {
    Update(
      ensureInstructionsDirExistsAndIsAccessibleFromPC,
      getInstructions,
      msgChan,
      updateChan)
  }

  val snackbarChan: FlowableProcessor<SnackbarMessage> = UnicastProcessor.create()

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
    ensureInstructionsDirExistsAndIsAccessibleFromPC.start()
    getInstructions.start()
    update.start()

    LeakCanary.install(this)
//    Stetho.initializeWithDefaults(this)

    // Init
    controller.ensureInstructionsDirExistsAndIsAccessibleFromPC()
    // controller.getInstructions()
  }
}
