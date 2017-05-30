package com.cws.image

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.cws.image.databinding.EnterPasswordDialogBinding
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.run
import timber.log.Timber
import java.util.concurrent.TimeUnit

class EnterPasswordDialogFragment : DialogFragment() {
  private val binding: EnterPasswordDialogBinding by lazy {
    DataBindingUtil.inflate<EnterPasswordDialogBinding>(
      LayoutInflater.from(activity), R.layout.enter_password_dialog, null, false)
  }
  private val authentication: Authentication by lazy {
    provideAuthentication(activity.applicationContext)
  }
  private val showErrorMessages: PublishSubject<Unit> by lazy {
    PublishSubject.create<Unit>()
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val errorMessages = binding.errorMessages

    val dialog = AlertDialog.Builder(activity)
      .setView(binding.root)
      .setTitle(R.string.enter_password)
      .setMessage(R.string.enter_password_to_exit_kiosk_mode_dialog_message)
      .setPositiveButton(android.R.string.ok, null)
      .setNegativeButton(android.R.string.cancel, null)
      .show()

    // 2017-04-19 Cort Spellman
    // In order for the dialog not to dismiss on button-push, the listener must
    // be set after the dialog is shown.
    // https://stackoverflow.com/questions/6142308/android-dialog-keep-dialog-open-when-button-is-pressed#6142413
    val submitPassword = dialog.getButton(Dialog.BUTTON_POSITIVE)
    submitPassword.setOnClickListener { _ ->
      exitKioskModeIfPasswordIsCorrect(binding.password.text.toString(), dialog)
    }

    val message = dialog.findViewById(android.R.id.message) as TextView
    val paddingTop = resources.getDimensionPixelSize(R.dimen.dialog_message_padding_top)
    val paddingBottom = message.paddingBottom
    val paddingLeft = message.paddingLeft
    val paddingRight = message.paddingRight
    message.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    Observable.merge(
      RxTextView.textChanges(binding.password),
      showErrorMessages
    )
      .debounce(300L, TimeUnit.MILLISECONDS)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { x ->
        when (x) {
          is CharSequence -> errorMessages.visibility = View.INVISIBLE
          is Unit -> errorMessages.visibility = View.VISIBLE
        }
      }

    return dialog
  }

  fun exitKioskModeIfPasswordIsCorrect(password: String, dialog: Dialog) {
    launch(CommonPool) {
      val res = authentication.isPasswordCorrect(password)
      when (res) {
        is Result.Err -> {
          Timber.e(
            Exception(
              "Cannot check whether kiosk-mode-unlock password is correct because there is no stored password."))
          exitKioskMode(
            "No unlock password was found. This kiosk mode is merely intended to be a theft/misuse deterrent so the device is being unlocked."
          )
          dialog.dismiss()
        }

        is Result.Ok -> {
          if (res.okValue) {
            exitKioskMode()
            dialog.dismiss()
          }
          else {
            run(UI) {
              Timber.d("Password is not correct. Cannot exit kiosk mode.")
              binding.password.setText("")
              showErrorMessages.onNext(Unit)
            }
          }
        }
      }
    }
  }

  fun exitKioskMode(message: String? = null) {
    (activity as? ExitKioskMode)?.exitKioskMode(message)
    ?: run {
      Toast.makeText(
        context,
        "Could not exit kiosk mode. Please try again.\n${getString(R.string.error_has_been_logged)}",
        Toast.LENGTH_LONG
      )
        .show()
      Timber.e("Could not exit kiosk mode because activity could not be cast to ExitKioskMode, which has the click-handler method. Activity: ${activity?.javaClass?.canonicalName}")
    }
  }
}
