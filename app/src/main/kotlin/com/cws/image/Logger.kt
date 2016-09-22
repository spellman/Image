package com.cws.image

import android.util.Log
import com.brianegan.bansa.Middleware

fun logger(tag: String) : Middleware<State> {
  return Middleware { store, action, next ->
    Log.d(tag, "--> ${action.javaClass.canonicalName.split(".").last()}\n${action.toString()}")
    next.dispatch(action)
    Log.d(tag, "<-- ${store.state}")
  }
}
