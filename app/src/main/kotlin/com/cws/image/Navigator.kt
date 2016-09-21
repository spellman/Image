package com.cws.image

import android.app.Activity
import android.content.Context
import com.brianegan.bansa.Middleware
import com.github.andrewoma.dexx.kollection.ImmutableList
import trikita.anvil.RenderableView

class NavigationStack(val scenes: ImmutableList<String>) {
  fun push(scene: String): NavigationStack {
    return NavigationStack(scenes.plus(scene))
  }

  fun pop(): NavigationStack {
    return NavigationStack(scenes.dropLast(1))
  }

  fun peek(): String {
    return scenes.last()
  }

  fun size(): Int {
    return scenes.size
  }

  fun isEmpty(): Boolean {
    return scenes.isEmpty()
  }
}

val navigator = Middleware<State> { store, action, next ->
  fun route(scene: String, context: Context): RenderableView {
    return when(scene) {
      "main" -> ViewMain(context)
      "instruction" -> ViewInstruction(context)
      else -> ViewMain(context)
    }
  }

  fun render(scene: String, activity: Activity) {
    activity.setContentView(route(scene, activity))
  }

  val activity = store.state.activity

  if (activity !is Activity) {
    next.dispatch(action)
  }
  else {
    when (action) {
      is Action.ShowCurrentView ->
        render(store.state.navigationStack.peek(), activity)

      is Action.NavigateTo -> {
        next.dispatch(action)
        render(store.state.navigationStack.peek(), activity)
      }

      is Action.NavigateBack -> {
        if (store.state.navigationStack.size() == 1
            || store.state.navigationStack.isEmpty()) {
          store.dispatch(Action.ClearActivity())
          activity.finish()
        } else {
          next.dispatch(action)
          render(store.state.navigationStack.peek(), activity)
        }
      }

      else -> next.dispatch(action)
    }
  }
}
