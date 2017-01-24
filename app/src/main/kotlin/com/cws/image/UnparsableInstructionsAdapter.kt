package com.cws.image

import android.content.Context

class UnparsableInstructionsAdapter(
  layoutId: Int,
  val context: Context,
  val instructions: MutableList<UnparsableInstructionViewModel>
) : SingleLayoutRecyclerViewDataBindingAdapter(layoutId) {
  override fun getItemForPosition(position: Int): UnparsableInstructionViewModel {
    return instructions[position]
  }

  override fun getItemCount(): Int {
    return instructions.count()
  }
}
