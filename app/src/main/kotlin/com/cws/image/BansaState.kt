package com.cws.image

import com.brianegan.bansa.Reducer
import com.github.andrewoma.dexx.kollection.*

data class Instruction(val subject: String,
                       val language: String,
                       val absolutePath: String,
                       val cueStartTime: Long)

sealed class Scene {
  class Main() : Scene() {
    override fun toString(): String { return this.javaClass.simpleName }
  }

  class Instruction() : Scene() {
    override fun toString(): String { return this.javaClass.simpleName }
  }
}

data class NavigationStack(val scenes: ImmutableList<Scene>) {
  fun push(scene: Scene): NavigationStack {
    return this.copy(scenes = scenes.plus(scene))
  }

  fun pop(): NavigationStack {
    return this.copy(scenes = scenes.dropLast(1))
  }

  fun peek(): Scene {
    return scenes.last()
  }

  fun size(): Int {
    return scenes.size
  }
}



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
data class BansaState(val isInitializing: Boolean,
                      val navigationStack: NavigationStack,
                      val needToRefreshInstructions: Boolean,
                      val canReadInstructionFiles: Boolean,
                      val canReadInstructionFilesMessage: String,
                      val instructions: ImmutableSet<Instruction>,
                      val unparsableInstructions: ImmutableSet<UnparsableInstruction>,
                      val languages: ImmutableSet<String>,
                      val language: String,
                      val instructionLoadingMessage: String?,
                      val countDownStartTime: Long,
                      val countDownDuration: Long,
                      val countDownValue: Long?,
                      val cueStartTime: Long,
                      val cueStopTime: Long,
                      val instructionAudioDuration: Long,
                      val subjectToDisplay: String?,
                      val languageToDisplay: String?,
                      val cueMessage: String?) {
  override fun toString(): String {
    return """${this.javaClass.canonicalName}:
               |isInitializing: ${isInitializing}
               |navigationStack: ${navigationStack}
               |needToRefreshInstructions: ${needToRefreshInstructions}
               |canReadInstructionFiles: ${canReadInstructionFiles}
               |canReadInstructionFilesMessage: ${canReadInstructionFilesMessage}
               |instructions: ImmutableSet(
               |                ${instructions.joinToString(",\n                ")})
               |unparsableInstructions: ImmutableSet(
               |                          ${unparsableInstructions.joinToString(",\n                          ")})
               |languages: ${languages}
               |language: ${language}
               |instructionLoadingMessage: ${instructionLoadingMessage}
               |countDownStartTime: ${countDownStartTime}
               |countDownDuration: ${countDownDuration}
               |countDownValue: ${countDownValue}
               |cueStartTime: ${cueStartTime}
               |cueStopTime: ${cueStopTime}
               |instructionAudioDuration: ${instructionAudioDuration}
               |subjectToDisplay: ${subjectToDisplay}
               |languageToDisplay: ${languageToDisplay}
               |cueMessage: ${cueMessage}""".trimMargin()
  }
}



sealed class Action : com.brianegan.bansa.Action {
  class DidInitialize() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class DoNeedToRefreshInstructions() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class RefreshInstructions() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class SetInstructionsAndLanguages(
      val canReadInstructionFiles: Boolean,
      val canReadInstructionFilesMessage: String,
      val instructions: ImmutableSet<Instruction>,
      val unparsableInstructions: ImmutableSet<UnparsableInstruction>) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |canReadInstructionFiles: ${canReadInstructionFiles}
               |canReadInstructionFilesMessage: ${canReadInstructionFilesMessage}
               |instructions: ImmutableSet(
               |        ${instructions.joinToString(",\n              ")})
               |unparsableInstructions: ImmutableSet(
               |                  ${unparsableInstructions.joinToString(",\n              ")})""".trimMargin()
    }
  }

  class NavigateTo(val scene: Scene) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |scene: ${scene}""".trimMargin()
    }
  }

  class NavigateBack() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class SetLanguage(val language: String) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |language: ${language}""".trimMargin()
    }
  }

  class PlayInstruction(val instruction: Instruction) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instruction: ${instruction}""".trimMargin()
    }
  }

  class CouldNotPlayInstruction(val instruction: Instruction) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instruction: ${instruction}""".trimMargin()
    }
  }

  class SetInstructionTimings(val cueStartTime: Long,
                              val instructionAudioDuration: Long) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |cueStartTime: ${cueStartTime}
               |instructionAudioDuration: ${instructionAudioDuration}""".trimMargin()
    }
  }

  class ClearInstructionLoadingMessage() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class SetCountDownValue(val countDownValue: Long) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |countDownValue: ${countDownValue}""".trimMargin()
    }
  }

  class SetCueMessage(val cueMessage: String) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |cueMessage: ${cueMessage}""".trimMargin()
    }
  }

  class ClearCueMessage() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class EndInstruction() : Action () {
    override fun toString(): String { return this.javaClass.canonicalName }
  }
}



val reducer = Reducer<BansaState> { state, action ->
  when (action) {
    is Action.DidInitialize ->
      state.copy(isInitializing = false)

    is Action.DoNeedToRefreshInstructions ->
      state.copy(needToRefreshInstructions = true)

    is Action.RefreshInstructions -> state

    is Action.SetInstructionsAndLanguages ->
      state.copy(needToRefreshInstructions = false,
                 canReadInstructionFiles = action.canReadInstructionFiles,
                 canReadInstructionFilesMessage = action.canReadInstructionFilesMessage,
                 instructions = action.instructions,
                 unparsableInstructions = action.unparsableInstructions,
                 languages = action.instructions.map { i -> i.language }.toImmutableSet())

    is Action.NavigateTo ->
      state.copy(navigationStack = state.navigationStack.push(action.scene))

    is Action.NavigateBack ->
      state.copy(navigationStack = state.navigationStack.pop())

    is Action.SetLanguage ->
      state.copy(language = action.language)

    is Action.PlayInstruction ->
      state.copy(instructionLoadingMessage = "loading",
                 subjectToDisplay = action.instruction.subject,
                 languageToDisplay = action.instruction.language)

    is Action.CouldNotPlayInstruction -> state

    is Action.SetInstructionTimings -> {
      val instructionAudioDuration = action.instructionAudioDuration
      val cueStartTime = action.cueStartTime
      val countDownDuration = if (cueStartTime > idealCountDownDuration) {
        idealCountDownDuration
      }
      else {
        (cueStartTime / 1000L) * 1000L
      }

      val cueStopTime =
          if (instructionAudioDuration > cueStartTime + idealCueDuration) {
            cueStartTime + idealCueDuration
          }
          else {
            instructionAudioDuration
          }

      state.copy(countDownStartTime = cueStartTime - countDownDuration,
                 countDownDuration = countDownDuration,
                 cueStartTime = cueStartTime,
                 cueStopTime = cueStopTime,
                 instructionAudioDuration = instructionAudioDuration)
    }

    is Action.ClearInstructionLoadingMessage ->
      state.copy(instructionLoadingMessage = null)

    is Action.SetCountDownValue ->
      state.copy(countDownValue = action.countDownValue)

    is Action.SetCueMessage ->
      state.copy(countDownValue = null,
                 cueMessage = action.cueMessage)

    is Action.ClearCueMessage ->
      state.copy(cueMessage = null)

    is Action.EndInstruction ->
      state.copy(instructionLoadingMessage = null,
                 countDownStartTime = 0,
                 countDownDuration = 0,
                 countDownValue = null,
                 cueStartTime = 0,
                 cueStopTime = 0,
                 instructionAudioDuration = 0,
                 subjectToDisplay = null,
                 languageToDisplay = null,
                 cueMessage = null)

    else ->
      throw IllegalArgumentException("No reducer case has been defined for the action of ${action}")
  }
}
