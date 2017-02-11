package com.cws.image

import android.os.Parcel
import android.os.Parcelable
import paperparcel.PaperParcel

@PaperParcel
data class Instruction(
  val subject: String,
  val language: String,
  val absolutePath: String,
  val cueStartTime: Long
) : Parcelable {
  companion object {
    @JvmField val CREATOR = PaperParcelInstruction.CREATOR
  }

  override fun describeContents() = 0

  override fun writeToParcel(dest: Parcel, flags: Int) {
    PaperParcelInstruction.writeToParcel(this, dest, flags)
  }
}

sealed class InstructionParsingFailure {
  class FileNameFormatFailure : InstructionParsingFailure()
  class CueTimeFailure : InstructionParsingFailure()
}

data class UnparsableInstruction(
  val fileName: String,
  val failure: InstructionParsingFailure
)
