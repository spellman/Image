package com.cws.image

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.support.design.widget.Snackbar
import com.github.andrewoma.dexx.kollection.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainPresenter(
  val activity: MainActivity,
  val getInstructions: GetInstructions,
  val authentication: Authentication,
  val kioskModeSetting: KioskModeSetting
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
  val canEnterKioskMode: Boolean by lazy {
    if (!devicePolicyManager.isDeviceOwnerApp(activity.packageName)) {
      false
    }
    else {
      devicePolicyManager.setLockTaskPackages(deviceAdminReceiver,
                                              arrayOf(activity.packageName))

      if (!devicePolicyManager.isLockTaskPermitted(activity.packageName)) {
        // TODO: This should not happen so log an error to Crashlytics.
        Timber.e(
          "DevicePolicyManager#isDeviceOwnerApp just returned true and we just made this a lo")
        false
      }
      else {

        true
      }
    }
  }

  fun showInstructions() {
    getInstructions.getInstructions()
      .toObservable()
      .retryWhen { errors ->
        errors
          .zipWith(Observable.range(4, 12),
                   BiFunction({ err: Throwable, i: Int -> i }))
          .flatMap { numberOfRetries ->
            // 2017-02-19 Cort Spellman
            // integral(2 ^ x, x, x = 4, x = 8, x <- Z) = 496
            // 496 milliseconds ~ a half-second delay
            if (numberOfRetries == 8) {
              activity.showSnackbarShort("Loading instructions...")
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
            parsedInstructions.icons
          )

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
          // TODO: Switch the unparsable instructions recyclerview to
          // databinding and the languages to a viewpager and databinding so
          // that you can change the viewmodel here instead of manipulating the
          // view manually.
          activity.refreshUnparsableInstructions(unparsableInstructionViewModels)
          activity.refreshLanguageTabs(languages)
        },
        { throwable ->
          val message = (throwable.message as? String)
                        ?: "Could not read instructions. (No reason given.)"
          Timber.e(IOException(message))
          activity.showSnackbarIndefinite(message)
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
    activity.showSnackbarShort("Could not play the selected item.")
  }

  fun couldNotPlayInstruction(instruction: InstructionViewModel?, message: String?) {
    val displayMessage =
      instruction?.let {
        "The ${it.language} ${it.subject} instruction could not be played.\n${message}"
      }
      ?: "The instruction could not be played.\n${message}"
    activity.showSnackbarShort(displayMessage)
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
        language = instruction.language.titlize(),
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

  fun instructionParsingFailureToMessage(parsingFailure: ParsingFailure) : String {
    val r = activity.resources
    val name = when (parsingFailure) {
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

  fun showInstructionsToSetUpLockedMode() {
    activity.showDialogWithInstructionsToSetUpKioskMode()
  }

  fun startWorkflowToSetPassword() {
    activity.showDialogToSetPassword()
  }

  fun setPassword(password: String) {
    Observable.just(authentication.setPassword(password))
      .map { res ->
        when (res) {
          is Result.Err -> throw Exception(res.errValue)
          is Result.Ok -> res.okValue
        }
      }
      .retryWhen { errors ->
        errors
          .zipWith(Observable.range(4, 12),
                   BiFunction({ error: Throwable, i: Int -> Pair(error, i) }))
          .flatMap { (err, numberOfRetries) ->
            // 2017-04-18 Cort Spellman
            // integral(2 ^ x, x, x = 4, x = 8, x <- Z) = 496
            // 496 milliseconds ~ a half-second delay
            if (numberOfRetries == 8) {
              activity.showSnackbarShort("Working...")
            }

            // 2017-02-19 Cort Spellman: Retry with exponential backoff.
            val durationUntilRetry =
              Math.pow(2.toDouble(), numberOfRetries.toDouble()).toLong()

            Timber.d("${err.message} Failure #${numberOfRetries}. Trying again in ${durationUntilRetry} milliseconds.")
            Observable.timer(durationUntilRetry, TimeUnit.MILLISECONDS) }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { _ ->
          activity.showSnackbarShort("Password set.")
        },
        { err ->
          Timber.e(err.message)
          activity.showSnackbarShort("Password could not be saved after several attempts. Trying again may work.\n${activity.getString(R.string.error_has_been_logged)}")
        }
      )
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
      Timber.e("We are trying to enter authorized lock-task mode when the device is not set up to do so. Why was the action available to the user?.")
      activity.showSnackbarShort("This device isn't set up for kiosk mode yet. Select ${activity.getString(R.string.set_up_kiosk_mode)} from the menu to set up the device.")
      return
    }

    Timber.d("App can enter lock task mode.")

    if (!authentication.isPasswordSet()) {
      Timber.e("We are trying to enter authorized lock-task mode before the unlock password has been set. Why was the action available to the user?.")
      activity.showSnackbarShort("You need to be able to unlock kiosk mode before you enter it. Select ${activity.getString(R.string.set_password)} from the menu to set your password.")
      return
    }

    Timber.d("A password has been set.")
    activity.startLockTask()
    setShouldBeInKioskMode(true)
    Timber.d("Entered kiosk mode.")
  }

  fun startWorkflowToExitKioskMode() {
    Timber.d("About to try to exit kiosk mode.")
    if (!isInLockTaskMode()) {
      Timber.e("We are trying to exit lock-task mode when the app is not in the mode. Why was the action available to the user?.")
      activity.showSnackbarShort("Oops, it looks like the device was already not in kiosk mode.")
      return
    }

    Timber.d("In lock task mode. About to show dialog to allow user to enter password to exit kisok mode.")
    activity.showDialogToEnterPasswordToExitKioskMode()
  }

  fun exitKioskMode(message: String? = null) {
    message?.let {
      activity.showSnackbarLong(
        "${it} ${activity.getString(R.string.error_has_been_logged)}"
      )
    }
    Timber.d("About to exit kiosk mode.")
    activity.stopLockTask()
    setShouldBeInKioskMode(false)
    Timber.d("Exited kiosk mode.")
  }

  fun resumeKioskModeIfItWasInterrupted() {
    if (kioskModeSetting.getShouldBeInKioskMode()
      && !isInLockTaskMode()) {
      enterKioskMode()
    }
  }

  fun isSetUpKioskModeMenuItemVisible(): Boolean {
    return !canEnterKioskMode
  }

  fun setPasswordMenuItemTitle(): String {
    return if (authentication.isPasswordSet()) {
      activity.getString(R.string.change_password)
    }
    else {
      activity.getString(R.string.set_password)
    }
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

  fun setShouldBeInKioskMode(shouldBeInKioskMode: Boolean) {
    Observable.just(kioskModeSetting.setShouldBeInKioskMode(shouldBeInKioskMode))
      .map { res ->
        when (res) {
          is Result.Err -> throw Exception(res.errValue)
          is Result.Ok -> res.okValue
        }
      }
      .retryWhen { errors ->
        errors
          .zipWith(Observable.range(4, 12),
                   BiFunction({ error: Throwable, i: Int -> Pair(error, i) }))
          .flatMap { (err, numberOfRetries) ->
            // 2017-04-18 Cort Spellman
            // integral(2 ^ x, x, x = 4, x = 8, x <- Z) = 496
            // 496 milliseconds ~ a half-second delay
            if (numberOfRetries == 8) {
              activity.showSnackbarShort("Working...")
            }

            // 2017-04-19 Cort Spellman: Retry with exponential backoff.
            val durationUntilRetry =
              Math.pow(2.toDouble(), numberOfRetries.toDouble()).toLong()

            Timber.d("${err.message} Failure #${numberOfRetries}. Trying again in ${durationUntilRetry} milliseconds.")
            Observable.timer(durationUntilRetry, TimeUnit.MILLISECONDS) }
      }
      .subscribeOn(Schedulers.io())
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe(
        { _ -> },
        { err ->
          Timber.e("${err.message}. Could not save kiosk-mode status.")
          val modeSpecificMessage = if (isInLockTaskMode()) {
            "The device is NOT secure. If it restarts (e.g., after losing power), it will not resume kiosk mode."
          }
          else {
            "This app will resume kiosk mode on quitting and restarting or if the device is physically rotated or on any number of other conditions that cause Android to re-create this screen."
          }
          activity.showSnackbar(
            message = "Kiosk-mode status could not be saved after several attempts.\n${modeSpecificMessage}\n${activity.getString(R.string.error_has_been_logged)}",
            duration = Snackbar.LENGTH_LONG,
            maxLines = 6
            )
        }
      )
  }
}
