package com.cws.image

import android.content.Context
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.github.andrewoma.dexx.kollection.ImmutableList

class UnparsableInstructionsAdapter(
  val layoutId: Int,
  val context: Context,
  var unparsableInstructions: ImmutableList<UnparsableInstructionViewModel>
) : RecyclerView.Adapter<RecyclerViewDataBindingViewHolder>() {
  override fun onCreateViewHolder(parent: ViewGroup?,
                                  viewType: Int): RecyclerViewDataBindingViewHolder {
    val binding = DataBindingUtil.inflate<ViewDataBinding>(
      LayoutInflater.from(parent?.context),
      viewType,
      parent,
      false)
    return RecyclerViewDataBindingViewHolder(binding)
  }

  override fun onBindViewHolder(holder: RecyclerViewDataBindingViewHolder?,
                                position: Int) {
    holder?.bind(getItemForPosition(position))
  }

  fun getLayoutIdForPosition(position: Int): Int {
    return layoutId
  }

  override fun getItemViewType(position: Int): Int {
    return getLayoutIdForPosition(position)
  }

  fun getItemForPosition(position: Int): UnparsableInstructionViewModel {
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
