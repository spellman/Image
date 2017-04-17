package com.cws.image

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.cws.image.databinding.SetPasswordDialogBinding
import com.jakewharton.rxbinding.widget.RxTextView
import hu.akarnokd.rxjava.interop.RxJavaInterop
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class SetPasswordDialogFragment : DialogFragment() {
  private val binding: SetPasswordDialogBinding by lazy {
    DataBindingUtil.inflate<SetPasswordDialogBinding>(
      LayoutInflater.from(activity), R.layout.set_password_dialog, null, false)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = AlertDialog.Builder(activity)
      .setView(binding.root)
      .setTitle(R.string.set_password_dialog_title)
      .setMessage(R.string.set_password_dialog_message)
      .setPositiveButton(
        android.R.string.ok,
        { _, _ ->
          (activity as MainActivity).setPassword(binding.newPassword.text.toString())
        })
      .setNegativeButton(android.R.string.cancel, null)
      .show()

    val message = dialog.findViewById(android.R.id.message) as TextView
    val paddingTop = resources.getDimensionPixelSize(R.dimen.dialog_message_padding_top)
    val paddingBottom = message.paddingBottom
    val paddingLeft = message.paddingLeft
    val paddingRight = message.paddingRight
    message.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    val setPasswordButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    setPasswordButton.isEnabled = false

    val errorMessages = binding.errorMessages

    val validator = validatePasswordWithConfirmation(activity)

    io.reactivex.Observable.combineLatest(
      RxJavaInterop.toV2Observable(RxTextView.textChanges(binding.newPassword)),
      RxJavaInterop.toV2Observable(RxTextView.textChanges(binding.confirmNewPassword)),
      BiFunction { password: CharSequence, passwordComfirmation: CharSequence ->
        Pair(password.toString(), passwordComfirmation.toString())
      }
    )
      .subscribeOn(AndroidSchedulers.mainThread())
      .debounce(300L, TimeUnit.MILLISECONDS)
      .observeOn(Schedulers.io())
      .map { (password, passwordConfirmation) ->
        Pair(password, validator.validate(password, passwordConfirmation))
      }
      .observeOn(AndroidSchedulers.mainThread())
      .forEach { (_, errors) ->
        errorMessages.text =
          errors.map { error -> "* ${error}" }.joinToString("\n")

        if (errors.isEmpty()) {
          errorMessages.visibility = View.GONE
          setPasswordButton.isEnabled = true
        }
        else {
          errorMessages.visibility = View.VISIBLE
          setPasswordButton.isEnabled = false
        }
      }

    return dialog
  }
}
