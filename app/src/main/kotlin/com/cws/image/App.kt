package com.cws.image

import android.app.Application
import android.content.Context
import android.os.Environment
import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import trikita.anvil.Anvil
import trikita.jedux.Action
import trikita.jedux.Logger
import trikita.jedux.Store
import java.io.File

val initialState =
    State(canReadInstructionsFiles = false,
          canReadInstructionsFilesMessage = "Initially assume instructions dir is not readable because it hasn't been checked for readability.",
          languages = immutableSetOf(),
          language = "english", // Should be system language. (What if there are no instructions in the system language? Show msg whenever no visible instructions, including then.)
          instructions = immutableSetOf(),
          instructionsBySubjectLanguagePair = immutableMapOf(),
          activity = null,
          navigationStack = NavigationStack(immutableListOf(NavigationFrame("main", null))))

fun playInstruction(instruction: Instruction): Action<Actions, Instruction> {
  return Action(Actions.PLAY_INSTRUCTION,
                instruction)
}

class Reducer(): Store.Reducer<Action<Actions, *>, State> {
  override fun reduce(action: Action<Actions, *>, state: State): State {
    val instructionFilesResult = reduceInstructionsAndLanguages(
                                   action,
                                   InstructionFilesResult(
                                       state.canReadInstructionsFiles,
                                       state.canReadInstructionsFilesMessage,
                                       state.instructions,
                                       state.instructionsBySubjectLanguagePair,
                                       state.languages))

    val navigationStackAndActivity = reduceNavigation(
                                       action,
                                       NavigationStackAndActivity(
                                           state.navigationStack,
                                           state.activity))

    return state.copy(
        canReadInstructionsFiles = instructionFilesResult.canReadInstructionFiles,
        canReadInstructionsFilesMessage = instructionFilesResult.canReadInstructionFilesMessage,
        languages = instructionFilesResult.languages,
        language = reduceLanguage(action, state.language),
        instructions = instructionFilesResult.instructions,
        instructionsBySubjectLanguagePair = instructionFilesResult.instructionsBySubjectLanguagePair,
        activity = navigationStackAndActivity.activity,
        navigationStack = navigationStackAndActivity.navigationStack)
  }
}

fun getLanguage(state: State): String {
  return state.language
}

fun getLanguages(state: State): ImmutableSet<String> {
  return state.languages
}



fun getLanguages(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>) : ImmutableSet<String> {
  return instructionsBySubjectLanguagePair.map { x -> x.key.language }.toImmutableSet()
}

fun getInstruction(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                   k: InstructionIdent): Instruction? {
  return instructionsBySubjectLanguagePair[k]
}

fun getInstructions(instructionsBySubjectLanguagePair: ImmutableMap<InstructionIdent, Instruction>,
                    ks: ImmutableSet<InstructionIdent>): ImmutableSet<Instruction> {
  return ks.mapNotNull { k -> getInstruction(instructionsBySubjectLanguagePair, k) }
           .toImmutableSet()
}

fun getInstructions(state: State): ImmutableSet<Instruction> {
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
                                  canReadInstructionFilesMessage = "No instructions found. Connect to the device (USB or otherwise) and copy instructions files to <device>/InternalStorage/${packageName}.",
                                  instructions = immutableSetOf())
    } else {
      setInstructionsAndLanguages(canReadInstructionFiles = true,
                                  canReadInstructionFilesMessage = "Read instructions from ${appDir.absolutePath}.",
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
                  Logger("Image") )

    val appDir: File = File(Environment.getExternalStorageDirectory(), packageName)

    LeakCanary.install(this)
    Stetho.initializeWithDefaults(this)
    store.subscribe(Anvil::render)
    store.dispatch(refreshInstructions(this as Context,
                                       appDir,
                                       requestUserCopyInstructionsToAppDir(packageName, appDir)))
  }
}
