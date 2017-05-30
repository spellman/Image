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
import com.cws.image.databinding.SetPasswordDialogBinding
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.jakewharton.rxbinding2.widget.RxTextView
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SetPasswordDialogFragment : DialogFragment() {
  private val binding: SetPasswordDialogBinding by lazy {
    DataBindingUtil.inflate<SetPasswordDialogBinding>(
      LayoutInflater.from(activity), R.layout.set_password_dialog, null, false)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val passwordMinLength = context.resources.getInteger(R.integer.password_min_length)

    val dialog = AlertDialog.Builder(activity)
      .setView(binding.root)
      .setTitle(resources.getString(R.string.set_password_dialog_title))
      .setMessage(
        """${resources.getString(R.string.set_password_dialog_message)}

    ${resources.getQuantityString(R.plurals.password_min_length, passwordMinLength, passwordMinLength)}
    ${context.getString(R.string.password_cannot_contain_password)}
    ${context.getString(R.string.password_cannot_contain_common_sequence)}"""
      )
      .setPositiveButton(
        android.R.string.ok,
        { _, _ -> setPassword(binding.newPassword.text.toString()) }
      )
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

    val passwordValidator = makePasswordValidator(activity)
    val passwordConfirmationValidator = makePasswordConfirmationValidator(activity)

    io.reactivex.Observable.combineLatest(
      RxTextView.textChanges(binding.newPassword).skipInitialValue(),
      RxTextView.textChanges(binding.confirmNewPassword),
      BiFunction { password: CharSequence, passwordComfirmation: CharSequence ->
        Pair(password.toString(), passwordComfirmation.toString())
      }
    )
      .subscribeOn(AndroidSchedulers.mainThread())
      .doOnEach { setPasswordButton.isEnabled = false }
      .debounce(500L, TimeUnit.MILLISECONDS)
      .observeOn(Schedulers.io())
      .map { (password, passwordConfirmation) ->
        val passwordErrors = passwordValidator.validate(password)
        val passwordConfirmationErrors =
          if (passwordConfirmation.isNotBlank()
              || (password.isNotBlank() && passwordErrors.isEmpty())) {
            passwordConfirmationValidator.validate(Pair(password, passwordConfirmation))
          }
          else {
            immutableListOf()
          }
        passwordErrors.plus(passwordConfirmationErrors)
      }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { errors ->
        errorMessages.text =
          errors.map { error -> "* ${error}" }.joinToString("\n")

        if (errors.isEmpty()) {
          errorMessages.visibility = View.INVISIBLE
          setPasswordButton.isEnabled = true
        }
        else {
          errorMessages.visibility = View.VISIBLE
          setPasswordButton.isEnabled = false
        }
      }

    return dialog
  }

  fun setPassword(password: String) {
    (activity as? SetPassword)?.setPassword(password)
    ?: run {
      Toast.makeText(
        context,
        "Could not set password. Please try again.\n${getString(R.string.error_has_been_logged)}",
        Toast.LENGTH_LONG
      )
        .show()
      Timber.e("Could not set password because activity could not be cast to SetPassword, which has the click-handler method. Activity: ${activity?.javaClass?.canonicalName}")
    }
  }
}
