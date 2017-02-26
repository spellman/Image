package com.cws.image

import com.github.andrewoma.dexx.kollection.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.comparisons.compareBy
import kotlin.comparisons.thenBy

class MainPresenter(
  val activity: MainActivity,
  val getInstructions: GetInstructions
) {
  var instructions: ImmutableSet<InstructionViewModel> = immutableSetOf()
  var selectedLanguage: String? = null

  fun showInstructions() {
    getInstructions.getInstructions()
      .toObservable()
      .retryWhen { errors ->
        errors
          .zipWith(Observable.range(4, 12),
                   BiFunction({ error: Throwable, i: Int -> i }))
          .flatMap { numberOfRetries ->
            // 2017-02-19 Cort Spellman
            // integral(2 ^ x, x, x = 4, x = 8, x <- Z) = 496
            // 496 milliseconds ~ a half-second delay
            if (numberOfRetries == 8) {
              // TODO: Show snackbar message "Loading..." or similar.
            }

            // 2017-02-19 Cort Spellman: Retry with exponential backoff.
            val durationUntilRetry =
              Math.pow(2.toDouble(), numberOfRetries.toDouble()).toLong()

            Timber.i("Unable to load instructions. Failure #${numberOfRetries}. Trying again in ${durationUntilRetry} milliseconds.")
            Observable.timer(durationUntilRetry, TimeUnit.MILLISECONDS) }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { parsedInstructions ->
          instructions = instructionsToInstructionViewModels(
            parsedInstructions.instructions,
            parsedInstructions.icons)

          val unparsableInstructionViewModels =
          parsedInstructions.unparsableFiles
              .map { u: UnparsableFile ->
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
          activity.refreshUnparsableInstructions(unparsableInstructionViewModels)
          activity.refreshLanguageTabs(languages)
        },
        { throwable ->
          val message = (throwable.message as? String)
                        ?: "Could not read instructions. (No reason given.)"
          Timber.e(IOException(message))
          activity.showMessageForInstructionsLoadFailure(message)
        }
      )
  }

  fun showInstructionsForLanguage(language: String) {
    // FIXME: Subjects must be mapped to resource strings, to be displayed in
    // the device's current language.

    // TODO: Can I do this in databinding instead?
    selectedLanguage = language
    activity.refreshInstructionsForCurrentLanguage(
      instructions.filter { i -> i.language == language }
        .sortedBy { i -> i.subject }
        .toImmutableList()
    )
  }

  fun playInstruction(instruction: InstructionViewModel) {
    activity.startPlayInstructionActivity(instruction)
  }

  fun couldNotPlayNonInstruction(
    item: Any?,
    positionInList: Int
  ) {
    Timber.e(
      Exception(
        "Cannot play non-instruction item ${item}, element number ${positionInList + 1} in the list of instructions."))
    activity.showMessageForInstructionPlayFailure("Could not play the selected item.")
  }

  fun couldNotPlayInstruction(instruction: InstructionViewModel?, message: String?) {
    val displayMessage =
      instruction?.let {
        "The ${it.language} ${it.subject} instruction could not be played.\n${message}"
      }
      ?: "The instruction could not be played.\n${message}"
    activity.showMessageForInstructionPlayFailure(displayMessage)
  }

  fun instructionsToInstructionViewModels(
    instructions: ImmutableSet<Instruction>,
    icons: ImmutableSet<Icon>
  ): ImmutableSet<InstructionViewModel> {
    val iconsBySubject = icons.map { icon -> Pair(icon.subject, icon) }.toImmutableMap()

    logMissingIcons(
      iconsBySubject,
      instructions.map { instruction -> instruction.subject}.toImmutableSet())

    return instructions.map { instruction ->
      InstructionViewModel(
        subject = titleCase(instruction.subject),
        language = instruction.language,
        audioAbsolutePath = instruction.absolutePath,
        cueStartTimeMilliseconds = instruction.cueStartTime,
        iconAbsolutePath = iconsBySubject[instruction.subject]?.absolutePath
      )
    }
      .toImmutableSet()
  }

  fun logMissingIcons(
    iconsBySubject: ImmutableMap<String, Icon>,
    subjects: ImmutableSet<String>
  ) {
    subjects.forEach { subject ->
      iconsBySubject[subject] ?: Timber.w("No icon for subject ${subject}")
    }
  }

  fun sortLanguages(ls : Iterable<String>) : List<String> {
    return ls.sortedWith(
      compareBy { l: String -> l != defaultLanguage() }
        .thenBy { l: String -> l })
  }

  fun titleCase(s: String): String {
    return s.split(" ").map { s -> s.capitalize() }.joinToString(" ")
  }

  fun defaultLanguage(): String {
    // TODO: The default language should be the saved default or if none has
    // been set, then the current device language, which may change.
    return "english"
  }

  fun instructionParsingFailureToMessage(f: ParsingFailure) : String {
    val r = activity.resources
    val name = when (f) {
      is ParsingFailure.FileNameEncoding ->
        "resource_file_name_encoding_failure_explanation"

      is ParsingFailure.FileFormat ->
        "resource_file_format_failure_explanation"

      is ParsingFailure.InstructionCueTime ->
        "instruction_cue_time_failure_explanation"

      is ParsingFailure.InstructionFileNameFormat ->
        "instruction_file_name_format_failure_explanation"
    }

    return r.getString(
      r.getIdentifier(name, "string", activity.packageName))
  }
}
