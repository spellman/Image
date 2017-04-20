package com.cws.image

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.cws.image.databinding.EnterPasswordDialogBinding
import com.jakewharton.rxbinding.widget.RxTextView
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.android.schedulers.AndroidSchedulers
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
  private val submitPasswordSubject: PublishSubject<Unit> by lazy {
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

    io.reactivex.Observable.merge(
      RxJavaInterop.toV2Observable(RxTextView.textChanges(binding.password)),
      submitPasswordSubject
    )
      .debounce(300L, TimeUnit.MILLISECONDS)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { x ->
        when (x) {
          is CharSequence -> errorMessages.visibility = View.GONE
          is Unit -> binding.errorMessages.visibility = View.VISIBLE
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
          val errorMessage = "No unlock password was found. This kiosk mode is merely intended to be a theft/misuse deterrent so the device is being unlocked."
          (activity as MainActivity).exitKioskMode(errorMessage)
          dialog.dismiss()
        }

        is Result.Ok -> {
          if (res.okValue) {
            (activity as MainActivity).exitKioskMode()
            dialog.dismiss()
          }
          else {
            run(UI) {
              Timber.d("Password is not correct. Cannot exit kiosk mode.")
              binding.password.setText("")
              submitPasswordSubject.onNext(Unit)
            }
          }
        }
      }
    }
  }
}
