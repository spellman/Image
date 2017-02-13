package com.cws.image

import android.util.Log
import com.github.andrewoma.dexx.kollection.*
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy

class MainPresenter(
  val activity: MainActivity,
  val getInstructions: GetInstructions
) {
  var instructions: ImmutableSet<Instruction> = immutableSetOf()
  var selectedLanguage: String? = null

  fun showInstructions() {
    Log.d(this.javaClass.simpleName, "About to get instructions")
    getInstructions.getInstructions()
      .doOnSuccess { parsedInstructions ->
        instructions = parsedInstructions.instructions
        Log.d(this.javaClass.simpleName, "Instructions: ${instructions}")
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { parsedInstructions ->
          val unparsableInstructionViewModels =
          parsedInstructions.unparsableInstructions
              .map { u: UnparsableInstruction ->
                UnparsableInstructionViewModel(
                  u.fileName,
                  instructionParsingFailureToMessage(u.failure)
                )
              }
            .sortedBy { u -> u.fileName }
            .toImmutableList()

          val languages = sortLanguages(
            instructions.map { i -> i.language }
              .distinct())
            .toImmutableList()

          // 2017-02-08 Cort Spellman
          // Switch the unparsable instructions recyclerview to databinding and the
          // languages to a viewpager and databinding so that you can change the
          // viewmodel here instead of manipulating the view manually.
          Log.d(this.javaClass.simpleName, "About to refresh unparsable instructions")
          activity.refreshUnparsableInstructions(unparsableInstructionViewModels)
          Log.d(this.javaClass.simpleName, "About to refresh language tabs")
          activity.refreshLanguageTabs(languages)
          Log.d(this.javaClass.simpleName, "About to set language to ${selectedLanguage}")
          activity.selectLanguageTab(languages.indexOf(selectedLanguage))
        },
        { throwable ->
          Log.d(this.javaClass.simpleName, "About to show message for failing to read instructions")
          activity.showMessageForInstructionsLoadFailure(
            (throwable.message as? String)
            ?: "Could not read instructions. (No reason given.)")
        }
      )
  }

  fun showInstructionsForLanguage(language: String) {
    // FIXME: Subjects must be mapped to resource strings, to be displayed in
    // the device's current language.
    // Can I do this in databinding instead?
    Log.d(this.javaClass.simpleName, "About to set language to ${language}")
    selectedLanguage = language
    activity.refreshInstructionsForCurrentLanguage(
      instructions.filter { i -> i.language == language }
        .sortedBy { i -> i.subject }
        .toImmutableList()
    )
  }

  fun playInstruction(instruction: Instruction) {
    activity.startPlayInstructionActivity(instruction)
  }

  fun couldNotPlayInstruction(instruction: Instruction?, message: String?) {
    val displayMessage =
      instruction?.let {
        "The ${it.language} ${it.subject} instruction could not be played.\n${message}"
      }
      ?: "The instruction could not be played."
    activity.showMessageForInstructionPlayFailure(displayMessage)
  }

  fun sortLanguages(ls : Iterable<String>) : List<String> {
    return ls.sortedWith(
      compareBy { l: String -> l != defaultLanguage() }
        .thenBy { l: String -> l })
  }

  fun defaultLanguage(): String {
    // TODO: The default language should be the saved default or if none has
    // been set, then the current device language, which may change.
    return "english"
  }

  fun instructionParsingFailureToMessage(f: InstructionParsingFailure) : String {
    val r = activity.resources
    val name = when (f) {
      is InstructionParsingFailure.FileNameFormatFailure ->
        "instruction_file_name_format_failure_explanation"

      is InstructionParsingFailure.CueTimeFailure ->
        "instruction_cue_time_failure_explanation"
    }

    return r.getString(
      r.getIdentifier(name, "string", activity.packageName))
  }
}
