package com.cws.image

import android.content.Context
import com.github.andrewoma.dexx.kollection.ImmutableList

class InstructionsAdapter(
  layoutId: Int,
  val context: Context,
  var instructions: ImmutableList<Instruction>
) : SingleLayoutRecyclerViewDataBindingAdapter(layoutId) {
  override fun getItemForPosition(position: Int): Instruction {
    return instructions[position]
  }

  override fun getItemCount(): Int {
    return instructions.count()
  }

  fun refreshInstructions(newInstructions: ImmutableList<Instruction>) {
    instructions = newInstructions
    notifyDataSetChanged()
  }
}
