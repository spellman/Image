package com.cws.image

import android.content.Context

class InstructionsAdapter(
  layoutId: Int,
  val context: Context,
  val instructions: MutableList<Instruction>
) : SingleLayoutRecyclerViewDataBindingAdapter(layoutId) {
  override fun getItemForPosition(position: Int): Instruction {
    return instructions[position]
  }

  override fun getItemCount(): Int {
    return instructions.count()
  }
}
