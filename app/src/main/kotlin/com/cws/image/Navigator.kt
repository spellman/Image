package com.cws.image

import android.app.Activity
import android.content.Context
import com.github.andrewoma.dexx.kollection.ImmutableList
import trikita.anvil.RenderableView
import trikita.jedux.Action
import trikita.jedux.Store

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

data class NavigationState(val navigationStack: NavigationStack,
                           val activity: Activity?)

fun setActivity(activity: Activity): Action<Actions, Activity> {
  return Action(Actions.SET_ACTIVITY, activity)
}

fun clearActivity(): Action<Actions, Nothing?> {
  return Action(Actions.CLEAR_ACTIVITY, null)
}

fun showCurrentView(): Action<Actions, Nothing?> {
  return Action(Actions.SHOW_CURRENT_VIEW, null)
}

fun navigateTo(scene: String): Action<Actions, String> {
  return Action(Actions.NAVIGATE_TO, scene)
}

fun navigateBack(): Action<Actions, Nothing?> {
  return Action(Actions.NAVIGATE_BACK, null)
}

fun reduceNavigation(action: Action<Actions, *>, state: NavigationState): NavigationState {
  return when(action.type) {
    Actions.SET_ACTIVITY -> state.copy(activity = action.value as Activity)

    Actions.CLEAR_ACTIVITY -> state.copy(activity = null)

    Actions.NAVIGATE_TO -> {
      state.copy(navigationStack = state.navigationStack.push(action.value as String))
    }

    Actions.NAVIGATE_BACK -> {
      state.copy(navigationStack = state.navigationStack.pop())
    }

    else -> state
  }
}

fun getCurrentScene(navigationState: NavigationState): String {
  return navigationState.navigationStack.peek()
}

// 2016-09-05 Cort Spellman
// TODO: Make this dynamic? Or maintain a map of scene to class and get the
// constructor for the class via reflection or whatever?
class Navigator: Store.Middleware<Action<Actions, *>, State> {
  fun makeView(scene: String, context: Context): RenderableView {
    return when(scene) {
      "main" -> MainView(context)
      "instruction" -> InstructionView(context)
      else -> MainView(context)
    }
  }

  fun render(scene: String, activity: Activity) {
    activity.setContentView(makeView(scene, activity))
  }

  override fun dispatch(store: Store<Action<Actions, *>, State>,
                        action: Action<Actions, *>,
                        next: Store.NextDispatcher<Action<Actions, *>>) {
    val activity = store.state.navigationState.activity

    if (activity !is Activity) next.dispatch(action)
    else {
      when (action.type) {
        Actions.SHOW_CURRENT_VIEW -> {
          render(getCurrentScene(store.state.navigationState), activity)
        }

        Actions.NAVIGATE_TO -> {
          next.dispatch(action)
          render(getCurrentScene(store.state.navigationState), activity)
        }

        Actions.NAVIGATE_BACK -> {
          if (store.state.navigationState.navigationStack.size() == 1
              || store.state.navigationState.navigationStack.isEmpty()) {
            activity.finish()
          } else {
            next.dispatch(action)
            render(getCurrentScene(store.state.navigationState), activity)
          }
        }

        else -> next.dispatch(action)
      }
    }
  }
}
