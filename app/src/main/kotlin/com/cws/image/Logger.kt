package com.cws.image

import android.util.Log
import com.brianegan.bansa.Middleware

fun logger(tag: String) : Middleware<State> {
  return Middleware { store, action, next ->
    if (action is Action.Tick) {
      if (action.time % 96 == 0L) {
        Log.d(tag, "--> tick: ${action.time}")
      }
    }
    else {
      Log.d(tag, "--> ${action.javaClass.canonicalName.split(".").last()}\n${action}")
      next.dispatch(action)
      Log.d(tag, "<-- ${store.state}")
    }
  }
}
