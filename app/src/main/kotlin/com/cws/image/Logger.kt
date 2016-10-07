package com.cws.image

import android.util.Log
import com.brianegan.bansa.Middleware
import com.brianegan.bansa.NextDispatcher
import com.brianegan.bansa.Store

class Logger(val tag: String) : Middleware<State> {
  override fun dispatch(store: Store<State>,
                        action: com.brianegan.bansa.Action,
                        next: NextDispatcher) {
    if (action is Action.Tick) {
      if ((action.time % 1000) <= tickDuration) {
        Log.d(tag, "--> tick: ${action.time}")
      }
      next.dispatch(action)
    }
    else {
      Log.d(tag, "--> ${action.javaClass.canonicalName.split(".").last()}\n${action}")
      next.dispatch(action)
      Log.d(tag, "<-- ${store.state}")
    }
  }
}
