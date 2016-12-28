package com.cws.image

import android.util.Log
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store

class Logger(val tag: String) : Middleware<BansaState> {
  override fun dispatch(store: Store<BansaState>,
                        action: com.brianegan.bansa.Action,
                        next: NextDispatcher) {
    Log.d(tag, "--> ${action.javaClass.canonicalName.split(".").last()}\n${action}")
    next.dispatch(action)
    Log.d(tag, "<-- ${store.state}")
  }
}
