package com.cws.image

abstract class SingleLayoutRecyclerViewDataBindingAdapter(val layoutId: Int) : BaseRecyclerViewDataBindingAdapter() {
  override fun getLayoutIdForPosition(position: Int): Int {
    return layoutId
  }
}

