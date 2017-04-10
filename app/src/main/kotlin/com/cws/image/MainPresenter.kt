package com.cws.image

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.github.andrewoma.dexx.kollection.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.Channel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.run
import kotlinx.coroutines.experimental.selects.selectUnbiased
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainPresenter(
  val activity: MainActivity,
  val getInstructions: GetInstructions,
  val authentication: Authentication
) {
  val activityManager by lazy {
    activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  }
  var instructions: ImmutableSet<InstructionViewModel> = immutableSetOf()
  var selectedLanguage: String? = null
  val devicePolicyManager by lazy {
    activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
  }
  val deviceAdminReceiver by lazy {
    ComponentName(activity.applicationContext, ImageDeviceAdminReceiver::class.java)
  }
  val canEnterKioskMode: Boolean by lazy { _canEnterKioskMode() }

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

  fun _canEnterKioskMode(): Boolean {
    if (!devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
      // TODO: This should not happen so log an error to Crashlytics.
      Timber.d("${activity.packageName} is not the device owner so we can't enter kiosk mode.")
      return false
    }

    Timber.d("${activity.packageName} is the device owner.")
    devicePolicyManager.setLockTaskPackages(deviceAdminReceiver,
                                            arrayOf(activity.packageName))

    if (!devicePolicyManager.isLockTaskPermitted(activity.packageName)) {
      // TODO: This should not happen so log an error to Crashlytics.
      Timber.d("Lock task mode is not permitted.")
      return false
    }

    return true
  }

  fun showInstructionsToSetUpLockedMode() {
    activity.showDialogWithInstructionsToSetUpKioskMode()
  }

  fun setPassword() {
    activity.showDialogToSetPassword()
  }

  fun setPassword(password: String) {
    launch(CommonPool) {
      val result = authentication.setPassword(password)

      run(UI) {
        when (result) {
          is Result.Err -> {
            // TODO: This should be extremely rare, if it happens at all, so log this.
            // TODO: Show feedback to user.
            Timber.d(result.errValue)
          }
          is Result.Ok -> {
            // TODO: Show feedback to user.
            Timber.d("Password set.")
          }
        }
      }
    }
  }

 fun isInLockTaskMode(): Boolean {
    if (Build.VERSION.SDK_INT in
      Build.VERSION_CODES.LOLLIPOP..Build.VERSION_CODES.LOLLIPOP_MR1) {
      // 2017-03-31 Cort Spellman
      // isInLockTaskMode is deprecated in API version 23.
      return activityManager.isInLockTaskMode
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      return activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    return false
  }

  fun enterKioskMode() {
    Timber.d("Trying to enter kiosk mode.")
    if (!canEnterKioskMode) {
      // TODO: Provide feedback to user.
      return
    }

    Timber.d("App can enter lock task mode.")

    if (!authentication.isPasswordSet()) {
      Timber.d("Cannot enter kiosk mode because password is not set.")
      // TODO: This should not happen so log an error to Crashlytics.
      // TODO: Provide feedback to user.
      return
    }

    Timber.d("Password has been set.")
    activity.startLockTask()
    // TODO: Provide feedback to user.
    Timber.d("Entered kiosk mode.")
  }

  fun exitKioskMode() {
    Timber.d("About to try to exit kiosk mode.")
    if (!isInLockTaskMode()) {
      Timber.d("Not in lock task mode. No need to exit.")
      // TODO: Provide feedback to user.
      return
    }

    activity.showDialogToEnterPasswordToExitKioskMode()
  }

  fun exitKioskModeIfPasswordIsCorrect(password: String) {
    launch(CommonPool) {
      if (authentication.isPasswordCorrect(password)) {
        run(UI) {
          activity.stopLockTask()
          // TODO: Provide feedback to user.
          Timber.d("Exited kiosk mode.")
        }
      }
      else {
        // TODO: Provide feedback to user.
      }
    }
  }

  fun isSetUpKioskModeMenuItemVisible(): Boolean {
    return !canEnterKioskMode
  }

  fun isSetPasswordMenuItemVisible(): Boolean {
    return canEnterKioskMode && !isInLockTaskMode()
  }

  fun isEnterKioskModeMenuItemVisible(): Boolean {
    return canEnterKioskMode && !isInLockTaskMode()
  }

  fun isEnterKioskModeMenuItemEnabled(): Boolean {
    return authentication.isPasswordSet()
  }

  fun isExitKioskModeMenuItemVisible(): Boolean {
    return canEnterKioskMode && isInLockTaskMode()
  }
}
