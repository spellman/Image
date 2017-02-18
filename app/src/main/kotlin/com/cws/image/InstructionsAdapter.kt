package com.cws.image

import android.content.Context
import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.graphics.Color
import android.graphics.ColorFilter
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.cws.image.databinding.SubjectLayoutBinding
import com.github.andrewoma.dexx.kollection.ImmutableList

class InstructionsAdapter(
  val layoutId: Int,
  val context: Context,
  var instructions: ImmutableList<InstructionViewModel>,
  val onItemClickHandler: ((view: View, position: Int, item: Any?) -> Unit)
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
    val instructionViewModel = getItemForPosition(position)
    holder?.binding?.root?.setOnClickListener { view ->
      onItemClickHandler.invoke(view, position, instructionViewModel)
    }
    holder?.bind(getItemForPosition(position))

    val subjectIconImageView =
      (holder?.binding as? SubjectLayoutBinding)?.subjectIcon

    if (instructionViewModel.iconAbsolutePath != null) {
      Glide.with(context)
        .load(instructionViewModel.iconAbsolutePath)
        .asBitmap()
        .into(subjectIconImageView)

    }
    else {
      subjectIconImageView?.setColorFilter(Color.parseColor("#FFB0B0B0"))
      subjectIconImageView?.setImageDrawable(
        ContextCompat.getDrawable(context, R.drawable.ic_subject_placeholder))
    }
  }

  fun getLayoutIdForPosition(position: Int): Int {
    return layoutId
  }

  override fun getItemViewType(position: Int): Int {
    return getLayoutIdForPosition(position)
  }

  fun getItemForPosition(position: Int): InstructionViewModel {
    return instructions[position]
  }

  override fun getItemCount(): Int {
    return instructions.count()
  }

  fun refreshInstructions(newInstructions: ImmutableList<InstructionViewModel>) {
    instructions = newInstructions
    notifyDataSetChanged()
  }
}
