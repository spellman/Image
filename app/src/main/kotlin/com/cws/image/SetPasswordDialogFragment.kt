package com.cws.image

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import com.cws.image.databinding.SetPasswordDialogBinding
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.jakewharton.rxbinding.widget.RxTextView
import io.reactivex.ObservableSource
import io.reactivex.functions.BiFunction

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
      .create()

    val setPasswordButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
    setPasswordButton.isEnabled = false

    val errorMessages = binding.errorMessages

    val validatorApplier = ValidatorApplier(
      immutableListOf(
        Validator(
          getString(R.string.password_min_length),
          { password, _ -> password.length >= 8}
        ),
        Validator(
          getString(R.string.password_cannot_contain_password),
          { password, _ -> Regex("p[a4@][s5]{2}w[o0]rd").containsMatchIn(password) }
        ),
        Validator(
          getString(R.string.passwords_must_match),
          { password, passwordConfirmation -> password == passwordConfirmation}
        )
      )
    )

    io.reactivex.Observable.combineLatest(
      RxTextView.textChanges(binding.newPassword) as ObservableSource<CharSequence>,
      RxTextView.textChanges(binding.confirmNewPassword) as ObservableSource<CharSequence>,
      BiFunction { password: CharSequence, passwordComfirmation: CharSequence ->
        Pair(password.toString(), passwordComfirmation.toString())
      }
    )
      .map { (password, passwordConfirmation) ->
        Pair(password, validatorApplier.validate(password, passwordConfirmation))
      }
      .forEach { (_, errors) ->
        errorMessages.text =
          errors.flatMap { error -> immutableListOf("* ", error) }.joinToString("\n")

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

data class Validator(val message: String, val fn: (String, String) -> Boolean)

class ValidatorApplier(val validators: Collection<Validator>) {
  fun validate(password: String, passwordConfirmation: String): ImmutableList<String> {
    return validators.fold(immutableListOf<String>()) { accMessages, validator ->
      if (validator.fn(password, passwordConfirmation)) {
        return accMessages
      }
      else {
        return accMessages.plus(validator.message)
      }
    }
  }
}
