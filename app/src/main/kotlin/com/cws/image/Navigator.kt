package com.cws.image

import android.app.Activity
import android.content.Context
import com.github.andrewoma.dexx.kollection.ImmutableList
import trikita.anvil.RenderableView
import trikita.jedux.Action
import trikita.jedux.Store

data class NavigationFrame(val scene: String, val props: Any?)

class NavigationStack(val frames: ImmutableList<NavigationFrame>) {
  fun push(nf: NavigationFrame): NavigationStack {
    return NavigationStack(frames.plus(nf))
  }

  fun pop(): NavigationStack {
    return NavigationStack(frames.dropLast(1))
  }

  fun peek(): NavigationFrame {
    return frames.last()
  }
}

data class NavigationActionValue(val value: Any?, val activity: Activity)

fun showCurrentView(activity: Activity): Action<Actions, NavigationActionValue> {
  return Action(Actions.SHOW_CURRENT_VIEW,
                NavigationActionValue(null, activity))
}

fun navigateTo(navigationFrame: NavigationFrame, activity: Activity): Action<Actions, NavigationActionValue> {
  return Action(Actions.NAVIGATE_TO,
                NavigationActionValue(navigationFrame, activity))
}

fun navigateBack(activity: Activity): Action<Actions, NavigationActionValue> {
  return Action(Actions.NAVIGATE_BACK,
                NavigationActionValue(null, activity))
}

fun reduceNavigation(action: Action<Actions, *>, state: NavigationStack): NavigationStack {
  return when(action.type) {
    Actions.NAVIGATE_TO -> {
      state.push(action.value as NavigationFrame)
    }

    Actions.NAVIGATE_BACK -> state.pop()

    else -> state
  }
}

class Navigator: Store.Middleware<Action<Actions, *>, State> {
  fun makeView(scene: String, props: Any?, context: Context): RenderableView {
    return when(scene) {
      "main" -> MainView(context)
      "instruction" -> InstructionView(context, props as InstructionProps)
      else -> MainView(context)
    }
  }

  fun render(activity: Activity, navigationStack: NavigationStack) {
    val frameToRender = navigationStack.peek()
    activity.setContentView(makeView(frameToRender.scene,
                                     frameToRender.props,
                                     activity))
  }

  override fun dispatch(store: Store<Action<Actions, *>, State>,
                        action: Action<Actions, *>,
                        next: Store.NextDispatcher<Action<Actions, *>>) {
    when(action.type) {
      Actions.SHOW_CURRENT_VIEW -> {
        val value = action.value as NavigationActionValue
        render(value.activity, store.state.navigationStack)
      }

      Actions.NAVIGATE_TO -> {
        val value = action.value as NavigationActionValue
        next.dispatch(Action(action.type, value.value))
        render(value.activity, store.state.navigationStack)
      }

      Actions.NAVIGATE_BACK -> {
        val value = action.value as NavigationActionValue
        if (store.state.navigationStack.frames.size == 1
            || store.state.navigationStack.frames.isEmpty()) {
          value.activity.finish()
        } else {
          next.dispatch(Action(action.type, value.value))
          render(value.activity, store.state.navigationStack)
        }
      }

      else -> next.dispatch(action)
    }
  }
}
