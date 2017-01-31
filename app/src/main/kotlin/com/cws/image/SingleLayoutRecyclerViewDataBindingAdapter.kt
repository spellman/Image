package com.cws.image

import android.view.View

abstract class SingleLayoutRecyclerViewDataBindingAdapter(
  val layoutId: Int,
  onItemClickHandler: ((view: View, position: Int, item: Any?) -> Unit)?
) : BaseRecyclerViewDataBindingAdapter(onItemClickHandler) {
  override fun getLayoutIdForPosition(position: Int): Int {
    return layoutId
  }
}

