package com.cws.image

import android.content.Context
import com.brianegan.bansa.Reducer
import com.github.andrewoma.dexx.kollection.*
import java.io.File

data class Instruction(val subject: String,
                       val language: String,
                       val path: String,
                       val cueStartTime: Long)

sealed class Scene {
  class Main() : Scene() {
    override fun toString(): String { return this.javaClass.canonicalName.split(".").last() }
  }

  class Instruction() : Scene() {
    override fun toString(): String { return this.javaClass.canonicalName.split(".").last() }
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
data class State(val navigationStack: NavigationStack,
                 val canReadInstructionFiles: Boolean,
                 val canReadInstructionFilesMessage: String,
                 val instructions: ImmutableSet<Instruction>,
                 val languages: ImmutableSet<String>,
                 val language: String,
                 val instructionToPlay: Instruction?,
                 val countDownStartTime: Long?,
                 val countDownDuration: Long?,
                 val countDownValue: Long?,
                 val cueStartTime: Long?,
                 val instructionAudioDuration: Long?,
                 val subjectToDisplay: String?,
                 val languageToDisplay: String?) {
  override fun toString(): String {
    return """${this.javaClass.canonicalName}:
               |navigationStack: ${navigationStack}
               |canReadInstructionFiles: ${canReadInstructionFiles}
               |canReadInstructionFilesMessage: ${canReadInstructionFilesMessage}
               |instructions: ImmutableSet(
               |              ${instructions.joinToString(",\n              ")})
               |languages: ${languages}
               |language: ${language}
               |instructionToPlay: ${instructionToPlay}
               |countDownStartTime: ${countDownStartTime}
               |countDownDuration: ${countDownDuration}
               |countDownValue: ${countDownValue}
               |cueStartTime: ${cueStartTime}
               |instructionAudioDuration: ${instructionAudioDuration}
               |subjectToDisplay: ${subjectToDisplay}
               |languageToDisplay: ${languageToDisplay}""".trimMargin()
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

  class SetInstructionTimings(val instructionAudioDuration: Long) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |instructionAudioDuration: ${instructionAudioDuration}""".trimMargin()
    }
  }

  class Tick(val tickDuration: Long,
             val time: Long) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |tickDuration: ${tickDuration}
               |time: ${time}""".trimMargin()
    }
  }

  class AbortInstructionSequence() : Action () {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class EndInstruction() : Action () {
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

    is Action.NavigateTo ->
      state.copy(navigationStack = state.navigationStack.push(action.scene))

    is Action.NavigateBack ->
      state.copy(navigationStack = state.navigationStack.pop())

    is Action.SetLanguage ->
      state.copy(language = action.language)

    is Action.PlayInstruction -> state.copy(instructionToPlay = action.instruction)

    is Action.SetInstructionTimings -> {
      if (state.instructionToPlay is Instruction) {
        val cueStartTime = state.instructionToPlay.cueStartTime
        val countDownDuration = if (cueStartTime > idealCountDownDuration) {
          idealCountDownDuration
        } else {
          (cueStartTime / 1000L) * 1000L
        }

        state.copy(countDownStartTime = cueStartTime - countDownDuration,
                   countDownDuration = countDownDuration,
                   cueStartTime = cueStartTime,
                   instructionAudioDuration = action.instructionAudioDuration)
      }
      else {
        state
      }
    }

    // TODO: Check whether tick affects each thing that might be affected,
    // update the state accordingly.
    is Action.Tick -> state

    is Action.AbortInstructionSequence -> state

    is Action.EndInstruction ->
      state.copy(instructionToPlay = null,
                 countDownStartTime = null,
                 countDownDuration = null,
                 countDownValue = null,
                 cueStartTime = null,
                 instructionAudioDuration = null,
                 subjectToDisplay = null,
                 languageToDisplay = null)

    else ->
      throw IllegalArgumentException("No reducer case has been defined for the action of ${action}")
  }
}
