package com.cws.image

import android.support.v4.app.Fragment

open class BaseFragment() : Fragment() {
  override fun onDestroy() {
    super.onDestroy()
    (activity.application as App).refWatcher.watch(this)
  }
}
