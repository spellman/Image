package com.cws.image

import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView

class RecyclerViewDataBindingViewHolder(val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
  fun bind(viewModel: Any) {
    binding.setVariable(BR.viewModel, viewModel)
    binding.executePendingBindings()
  }
}
