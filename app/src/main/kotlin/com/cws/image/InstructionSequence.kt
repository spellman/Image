package com.cws.image

import trikita.jedux.Action
import trikita.jedux.Store

class InstructionSequence: Store.Middleware<Action<Actions, *>, State> {
  override fun dispatch(store: Store<Action<Actions, *>, State>,
                        action: Action<Actions, *>,
                        next: Store.NextDispatcher<Action<Actions, *>>) {
    throw UnsupportedOperationException(
        "not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}
