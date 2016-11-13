package com.cws.image

import android.content.Context
import android.support.design.widget.CoordinatorLayout
import android.util.AttributeSet
import trikita.anvil.Anvil

abstract class RenderableViewCoordinatorLayout : CoordinatorLayout, Anvil.Renderable {

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

  constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
    context, attrs, defStyleAttr)

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    Anvil.mount(this, this)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    Anvil.unmount(this)
  }

  abstract override fun view()
}