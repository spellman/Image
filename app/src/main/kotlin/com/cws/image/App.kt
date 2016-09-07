package com.cws.image

import android.app.Application
import android.os.Environment
//import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import trikita.anvil.Anvil
import trikita.jedux.Action
import trikita.jedux.Store
import java.io.File

val initialNavigationState =
    NavigationState(
        activity = null,
        navigationStack = NavigationStack(immutableListOf("main"))
    )

val initialInstructionsState =
    InstructionsState(
        canReadInstructionFiles = false,
        canReadInstructionFilesMessage = "Initially assume instructions dir is not readable because it hasn't been checked for readability.",
        languages = immutableSetOf(),
        instructions = immutableSetOf(),
        instructionsBySubjectLanguagePair = immutableMapOf()
    )

//// 2016-09-02 Cort Spellman
//// Dummy data - for emulator.
//// TODO: Put some instructions in a development version of resources directory;
////       copy them to the emulator's storage.
//val initialInstructionsState =
//    InstructionsState(
//        canReadInstructionFiles = false,
//        canReadInstructionFilesMessage = "Initially assume instructions dir is not readable because it hasn't been checked for readability.",
//        languages = languages,
//        instructions = instructions,
//        instructionsBySubjectLanguagePair = instructionsBySubjectLanguagePair
//    )

val initialState =
    State(navigationState = initialNavigationState,
          instructionsState = initialInstructionsState,
          language = "english", // Should be system language. (What if there are no instructions in the system language? Show msg whenever no visible instructions, including then.)
          instruction = null
    )

fun playInstruction(instruction: Instruction): Action<Actions, Instruction> {
  return Action(Actions.PLAY_INSTRUCTION,
                instruction)
}

class Reducer(): Store.Reducer<Action<Actions, *>, State> {
  override fun reduce(action: Action<Actions, *>, state: State): State {
    return state.copy(
        navigationState = reduceNavigation(action, state.navigationState),
        instructionsState = reduceInstructionsAndLanguages(action, state.instructionsState),
        language = reduceLanguage(action, state.language),
        instruction = reduceInstruction(action, state.instruction)
    )
  }
}

fun getLanguage(state: State): String {
  return state.language
}

fun getLanguages(state: InstructionsState): ImmutableSet<String> {
  return state.languages
}



fun getLanguages(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>) : ImmutableSet<String> {
  return instructionsBySubjectLanguagePair.map { x -> x.key.language }.toImmutableSet()
}

fun getInstruction(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                   k: InstructionIdent): Instruction? {
  return instructionsBySubjectLanguagePair[k]
}



fun getInstruction(state: State): Instruction? {
  return state.instruction
}

fun getInstructions(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                    ks: ImmutableSet<InstructionIdent>): ImmutableSet<Instruction> {
  return ks.mapNotNull { k -> getInstruction(instructionsBySubjectLanguagePair, k) }
           .toImmutableSet()
}

fun getInstructions(state: InstructionsState): ImmutableSet<Instruction> {
  return getInstructions(state.instructionsBySubjectLanguagePair,
                         state.instructions)
}

fun getVisibleInstructions(instructions: ImmutableSet<Instruction>, language: String): ImmutableSet<Instruction> {
  return instructions.filter { i -> i.language == language }.toImmutableSet()
}

fun requestUserCopyInstructionsToAppDir(packageName: String, appDir: File): (ImmutableSet<Instruction>) -> Action<Actions, SetInstructionsAndLanguagesActionValue> {
  return  { instructions: ImmutableSet<Instruction> ->
    if (instructions.isEmpty()) {
      setInstructionsAndLanguages(canReadInstructionFiles = true,
                                  canReadInstructionFilesMessage = "No instructions found. We're loading files manually for now so do the following to get started: 1. Connect the device to your computer via USB. 2. Ensure the device is in file transfer mode: Swipe down from the top of the device screen; one of the notifications should say \"USB for charging\" or \"USB for photo transfer\" or \"USB for file transfers\" or something like that. If it isn't \"USB for file transfers\", then touch the notification and then select \"USB for file transfers\". 3. Open the device in your file explorer (Windows Explorer on Windows, Finder on Mac, etc.). 4. Copy your instruction sound-files to <device>/InternalStorage/${packageName}.",
                                  instructions = immutableSetOf())
    } else {
      setInstructionsAndLanguages(canReadInstructionFiles = true,
                                  canReadInstructionFilesMessage = "Found instructions in ${appDir.absolutePath}.",
                                  instructions = instructions)
    }
  }
}

class App : Application() {
  lateinit var store: Store<Action<Actions, *>, State>

  override fun onCreate() {
    super.onCreate()

    store = Store(Reducer(),
                  initialState,
                  InstructionFiles(),
                  Navigator(),
                  Logger("Image"))

    val appDir: File = File(Environment.getExternalStorageDirectory(), packageName)

    LeakCanary.install(this)
//    Stetho.initializeWithDefaults(this)
    store.subscribe(Anvil::render)
    store.dispatch(
        refreshInstructions(this,
                            appDir,
                            requestUserCopyInstructionsToAppDir(packageName, appDir)))
  }
}
