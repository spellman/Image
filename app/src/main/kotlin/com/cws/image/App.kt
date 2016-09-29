package com.cws.image

import android.app.Application
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
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

val initialState =
    State(
        navigationStack = NavigationStack(immutableListOf(Scene.Main())),
        canReadInstructionFiles = false,
        canReadInstructionFilesMessage = "Initially assume instructions dir is not readable because it hasn't been checked for readability.",
        instructions = immutableSetOf(),
        languages = immutableSetOf(),
        language = "english", // Should be system language. (What if there are no instructions in the system language? Show msg whenever no visible instructions, including then.),
        instructionToPlay = null,
        isInstructionAudioPrepared = false,
        isInstructionAudioFinished = false,
        isInstructionGraphicsPrepared = false,
        isInstructionGraphicsFinished = false,
        mediaPlayer = null,
        countDownStartTime = null,
        countDownDuration = 5000,
        countDownValue = null,
        cueStartTime = null,
        cueDuration = 3000,
        cue = null,
        subjectToDisplay = null,
        languageToDisplay = null
    )

fun requestUserCopyInstructionsToAppDir(packageName: String, appDir: File): (ImmutableSet<Instruction>) -> Action.SetInstructionsAndLanguages {
  return  { instructions: ImmutableSet<Instruction> ->
    if (instructions.isEmpty()) {
      Action.SetInstructionsAndLanguages(canReadInstructionFiles = true,
                                         canReadInstructionFilesMessage = "No instructions found. We're loading files manually for now so do the following to get started: 1. Connect the device to your computer via USB. 2. Ensure the device is in file transfer mode: Swipe down from the top of the device screen; one of the notifications should say \"USB for charging\" or \"USB for photo transfer\" or \"USB for file transfers\" or something like that. If it isn't \"USB for file transfers\", then touch the notification and then select \"USB for file transfers\". 3. Open the device in your file explorer (Windows Explorer on Windows, Finder on Mac, etc.). 4. Copy your instruction sound-files to <device>/InternalStorage/${packageName}.",
                                         instructions = immutableSetOf())
    } else {
      Action.SetInstructionsAndLanguages(canReadInstructionFiles = true,
                                         canReadInstructionFilesMessage = "Found instructions in ${appDir.absolutePath}.",
                                         instructions = instructions)
    }
  }
}

class App : Application() {
  lateinit var store: Store<State>

  override fun onCreate() {
    super.onCreate()

    store = BaseStore(initialState,
                      reducer,
                      instructionSequence,
                      instructionFiles,
                      logger("Image")
    )

    val appDir: File = File(Environment.getExternalStorageDirectory(), packageName)

    LeakCanary.install(this)
//    Stetho.initializeWithDefaults(this)
    store.subscribe { Anvil.render() }
    store.dispatch(
        Action.RefreshInstructions(
            this,
            appDir,
            requestUserCopyInstructionsToAppDir(packageName, appDir)))
  }
}
