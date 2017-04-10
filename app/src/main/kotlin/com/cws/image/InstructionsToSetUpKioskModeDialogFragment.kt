package com.cws.image

import android.app.Dialog
import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import com.cws.image.databinding.InstructionsToSetUpKioskModeDialogBinding

class InstructionsToSetUpKioskModeDialogFragment : DialogFragment() {
  private val binding: InstructionsToSetUpKioskModeDialogBinding by lazy {
    DataBindingUtil.inflate<InstructionsToSetUpKioskModeDialogBinding>(
      LayoutInflater.from(activity), R.layout.instructions_to_set_up_kiosk_mode_dialog, null, false)
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    return AlertDialog.Builder(activity)
      .setView(binding.root)
      .setTitle(R.string.instructions_to_set_up_kiosk_mode_title)
      .setPositiveButton(R.string.dismiss, null)
      .create()
  }
}
