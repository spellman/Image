package com.cws.image

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.widget.TextView
import com.cws.image.databinding.EnterPasswordDialogBinding

class EnterPasswordDialogFragment : DialogFragment() {
  private val binding: EnterPasswordDialogBinding by lazy {
    DataBindingUtil.inflate<EnterPasswordDialogBinding>(
      LayoutInflater.from(activity), R.layout.enter_password_dialog, null, false)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = AlertDialog.Builder(activity)
      .setView(binding.root)
      .setTitle(R.string.enter_password)
      .setMessage(R.string.enter_password_to_exit_kiosk_mode_dialog_message)
      .setPositiveButton(
        android.R.string.ok,
        { _, _ ->
          (activity as MainActivity).exitKioskModeIfPasswordIsCorrect(binding.password.text.toString())
        })
      .setNegativeButton(android.R.string.cancel, null)
      .create()

    val message = dialog.findViewById(android.R.id.message) as TextView
    val paddingTop = resources.getDimensionPixelSize(R.dimen.dialog_message_padding_top)
    val paddingBottom = message.paddingBottom
    val paddingLeft = message.paddingLeft
    val paddingRight = message.paddingRight
    message.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

    return dialog
  }
}
