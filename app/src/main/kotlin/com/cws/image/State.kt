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

// 2016-09-23 Cort Spellman
// NOTE: All this overriding toString clutters stuff up but it's temporary:
// I plan to have a server component, running Clojure.
//   ==> Serialize stuff to EDN to send over the wire.
//         ==> Log the EDN.

// 2016-09-05 Cort Spellman
// TODO: Change canReadInstructionFilesMessage to a value (collection?), such
// that those values are in bijective correspondence with the possible
// situations involving reading files that I want to recognize / log.
// I don't want to be restricted to a string message; I probably want to show
// a certain view or at least format the text a certain way.
// Moreover, I want to make use of Android's resources localization stuff and
// change what and how something is displayed like any other view.
data class State(val navigationStack: NavigationStack,
                 val canReadInstructionFiles: Boolean,
                 val canReadInstructionFilesMessage: String,
                 val instructions: ImmutableSet<Instruction>,
                 val languages: ImmutableSet<String>,
                 val language: String,
                 val instructionToDisplay: Instruction?,
                 val instructionToPlay: Instruction?) {
  override fun toString(): String {
    return """${this.javaClass.canonicalName}:
               |navigationStack: ${navigationStack}
               |canReadInstructionFiles: ${canReadInstructionFiles}
               |canReadInstructionFilesMessage: ${canReadInstructionFilesMessage}
               |instructions: ImmutableSet(
               |              ${instructions.joinToString(",\n              ")})
               |languages: ${languages}
               |language: ${language}
               |instructionToDisplay: ${instructionToDisplay}
               |instructionToPlay: ${instructionToPlay}""".trimMargin()
  }
}

sealed class Action : com.brianegan.bansa.Action {
  class RefreshInstructions(
      val context: Context,
      val appDir: File,
      val instructionFilesUpdateFn: (ImmutableSet<Instruction>) -> Action.SetInstructionsAndLanguages
  ) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |context: ${context.javaClass.canonicalName}
               |appDir: ${appDir.name}
               |instructionFilesUpdateFn: ${instructionFilesUpdateFn}""".trimMargin()
    }
  }

  class SetInstructionsAndLanguages(
      val canReadInstructionFiles: Boolean,
      val canReadInstructionFilesMessage: String,
      val instructions: ImmutableSet<Instruction>
  ) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |canReadInstructionFiles: ${canReadInstructionFiles}
               |canReadInstructionFilesMessage: ${canReadInstructionFilesMessage}
               |instructions: ImmutableSet(
               |              ${instructions.joinToString(",\n              ")})""".trimMargin()
    }
  }

  class NavigateTo(val scene: Scene) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |scene: ${scene}""".trimMargin()
    }
  }

  class NavigateBack(val activity: Activity) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |activity: ${activity.javaClass.canonicalName}""".trimMargin()
    }
  }

  class SetLanguage(val language: String) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |language: ${language}""".trimMargin()
    }
  }

  class SetInstruction(val instruction: Instruction) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instruction: ${instruction}""".trimMargin()
    }
  }

  class ClearInstructionToDisplay() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class ClearInstructionToPlay() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class PrepareInstructionSequence() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class StartInstructionSequence() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class FinishInstructionSequence() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }
}

val reducer = Reducer<State> { state, action ->
  when (action) {
    is Action.RefreshInstructions -> state
    is Action.SetInstructionsAndLanguages ->
      state.copy(canReadInstructionFiles = action.canReadInstructionFiles,
                 canReadInstructionFilesMessage = action.canReadInstructionFilesMessage,
                 instructions = action.instructions,
                 languages = action.instructions.map { i -> i.language }.toImmutableSet())
    is Action.NavigateTo -> state.copy(navigationStack = state.navigationStack.push(action.scene))
    is Action.NavigateBack -> state.copy(navigationStack = state.navigationStack.pop())
    is Action.SetLanguage -> state.copy(language = action.language)
    is Action.SetInstruction -> state.copy(instructionToDisplay = action.instruction,
                                           instructionToPlay = action.instruction)
    is Action.ClearInstructionToDisplay -> state.copy(instructionToDisplay = null)
    is Action.ClearInstructionToPlay -> state.copy(instructionToPlay = null)
    is Action.PrepareInstructionSequence -> state
    is Action.StartInstructionSequence -> state
    is Action.FinishInstructionSequence -> state
    else -> throw IllegalArgumentException("No reducer case has been defined for the action of ${action}")
  }
}
