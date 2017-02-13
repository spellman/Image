package com.cws.image

import android.databinding.DataBindingUtil
import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

abstract class BaseRecyclerViewDataBindingAdapter(
  val onItemClickHandler: ((view: View, position: Int, item: Any?) -> Unit)?
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
    onItemClickHandler?.let {
      holder?.binding?.root?.setOnClickListener { view ->
        onItemClickHandler.invoke(view,
                                  position,
                                  getItemForPosition(position))
      }
    }
    holder?.bind(getItemForPosition(position))
  }

  override fun getItemViewType(position: Int): Int {
    return getLayoutIdForPosition(position)
  }

  protected abstract fun getItemForPosition(position: Int): Any
  protected abstract fun getLayoutIdForPosition(position: Int): Int
}
