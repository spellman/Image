package com.cws.image

import android.content.Context
import android.view.View
import com.github.andrewoma.dexx.kollection.ImmutableList

class UnparsableInstructionsAdapter(
  layoutId: Int,
  val context: Context,
  var unparsableInstructions: ImmutableList<UnparsableInstructionViewModel>,
  onItemClickHandler: ((view: View, position: Int, item: Any?) -> Unit)?
) : SingleLayoutRecyclerViewDataBindingAdapter(layoutId, onItemClickHandler) {
  override fun getItemForPosition(position: Int): UnparsableInstructionViewModel {
    return unparsableInstructions[position]
  }

  override fun getItemCount(): Int {
    return unparsableInstructions.count()
  }

  fun refreshUnparsableInstructions(newUnparsableInstructions: ImmutableList<UnparsableInstructionViewModel>) {
    unparsableInstructions = newUnparsableInstructions
    notifyDataSetChanged()
  }
}
