package com.cws.image

import android.content.Context
import android.media.MediaPlayer
import com.brianegan.bansa.Reducer
import com.github.andrewoma.dexx.kollection.*
import java.io.File

data class Instruction(val subject: String,
                       val language: String,
                       val path: String,
                       val cueTime: Int)

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
                 val isInstructionAudioPrepared: Boolean,
                 val isInstructionGraphicsPrepared: Boolean,
                 val isInstructionAudioFinished: Boolean,
                 val isInstructionGraphicsFinished: Boolean,
                 val mediaPlayer: MediaPlayer?,
                 val countDownStartTime: Int?,
                 val countDownDuration: Int,
                 val countDownValue: Int?,
                 val cueStartTime: Int?,
                 val cueDuration: Int,
                 val cue: String?,
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
               |isInstructionAudioPrepared: ${isInstructionAudioPrepared}
               |isInstructionGraphicsPrepared: ${isInstructionGraphicsPrepared}
               |isInstructionAudioFinished: ${isInstructionAudioFinished}
               |isInstructionGraphicsFinished: ${isInstructionGraphicsFinished}
               |mediaPlayer: ${mediaPlayer}
               |countDownStartTime: ${countDownStartTime}
               |countDownDuration: ${countDownDuration}
               |countDownValue: ${countDownValue}
               |cueStartTime: ${cueStartTime}
               |cueDuration: ${cueDuration}
               |cue: ${cue}
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

  class InstructionAudioPrepared(val mediaPlayer: MediaPlayer) : Action() {
    override fun toString(): String {
      return """${this.javaClass.canonicalName}:
               |mediaPlayer: ${mediaPlayer}""".trimMargin()
    }
  }

  class InstructionGraphicsPrepared() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class InstructionSequencePrepared() : Action() {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  // 2016-09-26 Cort Spellman
  // TODO: You do NOT want to log every tick.
  class Tick(val time: Int) : Action()

  class InstructionAudioFinished() : Action () {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class InstructionGraphicsFinished() : Action () {
    override fun toString(): String { return this.javaClass.canonicalName }
  }

  class InstructionSequenceFinished() : Action () {
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

    is Action.PlayInstruction ->
      state.copy(instructionToPlay = action.instruction,
                 isInstructionAudioFinished = false,
                 isInstructionGraphicsFinished = false)

    is Action.InstructionAudioPrepared -> {
      val cueStartTime = state.instructionToPlay?.cueTime
      if (cueStartTime is Int) {
        val countDownStartTime = Math.max(0, cueStartTime)
        state.copy(isInstructionAudioPrepared = true,
                   mediaPlayer = action.mediaPlayer,
                   countDownStartTime = countDownStartTime,
                   cueStartTime = cueStartTime)
      }
      else {
        // 2016-09-26 Cort Spellman
        // TODO: Dispatch action: Display error to user, alert me to error.
        state
      }
    }

    is Action.InstructionGraphicsPrepared ->
      state.copy(isInstructionGraphicsPrepared = true)

    is Action.InstructionSequencePrepared -> {
      val i = state.instructionToPlay
      if (i is Instruction) {
        state.copy(subjectToDisplay = i.subject,
                   languageToDisplay = i.language)
      }
      else {
        // 2016-09-26 Cort Spellman
        // TODO: Dispatch action: Display error to user, alert me to error.
        state
      }
    }

    is Action.Tick -> {
      // TODO: Check whether tick affects each thing that might be affected,
      // update the state accordingly.
    }

    is Action.InstructionAudioFinished ->
      state.copy(isInstructionAudioFinished = true,
                 mediaPlayer = null)

    is Action.InstructionGraphicsFinished ->
      state.copy(isInstructionGraphicsFinished = true,
                 countDownStartTime = null,
                 cueStartTime = null,
                 cue = null,
                 subjectToDisplay = null,
                 languageToDisplay = null)

    is Action.InstructionSequenceFinished ->
      state.copy(isInstructionAudioPrepared = false,
                 isInstructionGraphicsPrepared = false)

    else ->
      throw IllegalArgumentException("No reducer case has been defined for the action of ${action}")
  }
}
