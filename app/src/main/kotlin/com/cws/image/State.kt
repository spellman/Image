package com.cws.image

import android.content.Context
import android.util.Log
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

data class OnTickStateUpdate(val pred: (Long) -> Boolean, val updateFn: (Long, State) -> State)

data class OnTickAction(val pred: (Long) -> Boolean, val action: (Long, State) -> Unit)



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
               |navigationStack: ${navigationStack}
               |canReadInstructionFiles: ${canReadInstructionFiles}
               |canReadInstructionFilesMessage: ${canReadInstructionFilesMessage}
               |instructions: ImmutableSet(
               |              ${instructions.joinToString(",\n              ")})
               |languages: ${languages}
               |language: ${language}
               |instructionToPlay: ${instructionToPlay}
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

    is Action.PlayInstruction ->
      state.copy(instructionToPlay = action.instruction,
                 instructionLoadingMessage = "loading",
                 subjectToDisplay = action.instruction.subject,
                 languageToDisplay = action.instruction.language)

    is Action.SetInstructionTimings -> {
      // 2016-10-05 Cort Spellman
      // TODO: Check if state.instructionToPlay is an instruction.
      // Dispatch an action to trigger an error message if not.
      state.instructionToPlay as Instruction
      val instructionAudioDuration = action.instructionAudioDuration
      val cueStartTime = state.instructionToPlay.cueStartTime
      val countDownDuration = if (cueStartTime > idealCountDownDuration) {
                                idealCountDownDuration
                              } else {
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

    is Action.Tick -> {
      // 2016-10-05 Cort Spellman
      // TODO: Assertions on sequence of play, prep, start, finish, end?
      val time = action.time
      val timeIs = isWithin(action.tickDuration, time)
      val updates =
          immutableListOf(
              OnTickStateUpdate(
                { t -> timeIs(0) },
                { t, state -> state.copy(instructionLoadingMessage = null) }
              ),

              OnTickStateUpdate(
                { t ->
                    t % 1000 <= tickDuration
                      && Interval("[", state.countDownStartTime, state.cueStartTime - 1000, "]")
                       .contains(t, tickDuration) },
                { t, state ->
                    state.copy(
                      countDownValue = Math.round((state.countDownStartTime  - t + state.countDownDuration) / 1000.0).toLong()) }),

              OnTickStateUpdate(
                { t -> timeIs(state.cueStartTime) },
                { t, state ->
                    state.copy(countDownValue = null,
                               cueMessage = "Take the image now.") }),

              OnTickStateUpdate(
                { t -> timeIs(state.cueStopTime) },
                { t, state -> state.copy(cueMessage = null) })
      )

      updates.fold(state) { acc: State, onTickStateUpdate: OnTickStateUpdate ->
                            if (onTickStateUpdate.pred(time)) {
                              Log.d("ON-TICK ST UPDATE", "time: ${time}")
                              Log.d("ON-TICK ST UPDATE", acc.toString())
                              onTickStateUpdate.updateFn(time, acc)
                            }
                            else {
                              acc
                            } }
    }

    is Action.AbortInstructionSequence -> state

    is Action.EndInstruction ->
      state.copy(instructionToPlay = null,
                 instructionLoadingMessage = null,
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
