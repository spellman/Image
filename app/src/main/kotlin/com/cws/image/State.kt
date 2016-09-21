package com.cws.image

import android.app.Activity
import android.content.Context
import com.brianegan.bansa.Reducer
import com.github.andrewoma.dexx.kollection.*
import java.io.File

data class Instruction(val subject: String,
                       val language: String,
                       val path: String,
                       val cueTiming: Int)

// 2016-09-05 Cort Spellman
// TODO: Change canReadInstructionFilesMessage to a value (collection?), such
// that those values are in bijective correspondence with the possible
// situations involving reading files that I want to recognize / log.
// I don't want to be restricted to a string message; I probably want to show
// a certain view or at least format the text a certain way.
// Moreover, I want to make use of Android's resources localization stuff and
// change what and how something is displayed like any other view.
data class State(val navigationStack: NavigationStack,
                 val activity: Activity?,
                 val canReadInstructionFiles: Boolean,
                 val canReadInstructionFilesMessage: String,
                 val instructions: ImmutableSet<Instruction>,
                 val languages: ImmutableSet<String>,
                 val language: String,
                 val instructionToDisplay: Instruction?,
                 val instructionToPlay: Instruction?)

sealed class Action : com.brianegan.bansa.Action {
  data class RefreshInstructions(
      val context: Context,
      val appDir: File,
      val instructionsFilesUpdateFn: (ImmutableSet<Instruction>) -> Action.SetInstructionsAndLanguages
  ) : com.brianegan.bansa.Action

  data class SetInstructionsAndLanguages(
      val canReadInstructionFiles: Boolean,
      val canReadInstructionFilesMessage: String,
      val instructions: ImmutableSet<Instruction>
  ) : com.brianegan.bansa.Action

  data class SetActivity(val activity: Activity) : com.brianegan.bansa.Action
  class ClearActivity() : com.brianegan.bansa.Action
  class ShowCurrentView() : com.brianegan.bansa.Action
  data class NavigateTo(val scene: String) : com.brianegan.bansa.Action
  class NavigateBack() : com.brianegan.bansa.Action
  data class SetLanguage(val language: String) : com.brianegan.bansa.Action
  data class SetInstruction(val instruction: Instruction) : com.brianegan.bansa.Action
  class PrepareInstructionSequence() : com.brianegan.bansa.Action
  class StartInstructionSequence() : com.brianegan.bansa.Action
  class FinishInstructionSequence() : com.brianegan.bansa.Action
}

val reducer = Reducer<State> { state, action ->
  when (action) {
    is Action.RefreshInstructions -> state
    is Action.SetInstructionsAndLanguages ->
      state.copy(canReadInstructionFiles = action.canReadInstructionFiles,
                 canReadInstructionFilesMessage = action.canReadInstructionFilesMessage,
                 instructions = action.instructions,
                 languages = action.instructions.map { i -> i.language }.toImmutableSet())
    is Action.SetActivity -> state.copy(activity = action.activity)
    is Action.ClearActivity -> state.copy(activity = null)
    is Action.ShowCurrentView -> state
    is Action.NavigateTo -> state.copy(navigationStack = state.navigationStack.push(action.scene))
    is Action.NavigateBack -> state.copy(navigationStack = state.navigationStack.pop())
    is Action.SetLanguage -> state.copy(language = action.language)
    is Action.SetInstruction -> state.copy(instructionToDisplay = action.instruction,
                                           instructionToPlay = action.instruction)
    is Action.PrepareInstructionSequence -> state
    is Action.StartInstructionSequence -> state
    is Action.FinishInstructionSequence -> state
    else -> throw IllegalArgumentException("Unhandled action: ${action}")
  }
}
