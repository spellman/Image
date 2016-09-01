package com.cws.image

import android.util.Log
import trikita.jedux.Action
import trikita.jedux.Store

class Logger(val tag: String): Store.Middleware<Action<Actions, *>, State> {
  override fun dispatch(store: Store<Action<Actions, *>, State>,
                        action: Action<Actions, *>,
                        next: Store.NextDispatcher<Action<Actions, *>>) {
    Log.d(tag, "--> " + action.type + "\n" + action.value)
    next.dispatch(action)
    Log.d(tag, "<-- " + store.state)
  }
}
