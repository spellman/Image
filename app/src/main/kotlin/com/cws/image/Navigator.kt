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

fun setActivity(activity: Activity): Action<Actions, Activity> {
  return Action(Actions.SET_ACTIVITY, activity)
}

fun clearActivity(): Action<Actions, Nothing?> {
  return Action(Actions.CLEAR_ACTIVITY, null)
}

fun updateCurrentViewProps(props: Any?): Action<Actions, Any?> {
  return Action(Actions.UPDATE_CURRENT_VIEW_PROPS, props)
}

fun showCurrentView(): Action<Actions, Nothing?> {
  return Action(Actions.SHOW_CURRENT_VIEW, null)
}

fun navigateTo(navigationFrame: NavigationFrame): Action<Actions, NavigationFrame> {
  return Action(Actions.NAVIGATE_TO, navigationFrame)
}

fun navigateBack(): Action<Actions, Nothing?> {
  return Action(Actions.NAVIGATE_BACK, null)
}

data class NavigationStackAndActivity(val navigationStack: NavigationStack,
                                      val activity: Activity?)

fun reduceNavigation(action: Action<Actions, *>, state: NavigationStackAndActivity): NavigationStackAndActivity {
  val navigationStack = state.navigationStack

  return when(action.type) {
    Actions.SET_ACTIVITY -> state.copy(activity = action.value as Activity)

    Actions.CLEAR_ACTIVITY -> state.copy(activity = null)

  // 2016-08-30 Cort Spellman
  // TODO: Why does it not re-render on updating language or navigating to
  // instruction? Why does it render instruction after navigating to it and
  // then pushing back?
    Actions.UPDATE_CURRENT_VIEW_PROPS -> {
      val updatedCurrent = navigationStack.peek().copy(props = action.value)
      state.copy(navigationStack = navigationStack.pop().push(updatedCurrent))
    }

    Actions.NAVIGATE_TO -> {
      state.copy(navigationStack = navigationStack.push(action.value as NavigationFrame))
    }

    Actions.NAVIGATE_BACK -> state.copy(navigationStack = navigationStack.pop())

    else -> state
  }
}

class Navigator: Store.Middleware<Action<Actions, *>, State> {
  fun makeView(scene: String, props: Any?, context: Context): RenderableView {
    return when(scene) {
      "main" -> MainView(context, props as MainProps)
      "instruction" -> InstructionView(context, props as InstructionProps)
      else -> MainView(context, props as MainProps)
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
    val activity = store.state.activity
    val navigationStack = store.state.navigationStack

    if (activity !is Activity) next.dispatch(action)
    else {
      when (action.type) {
        Actions.SHOW_CURRENT_VIEW -> {
          render(activity, navigationStack)
        }

        Actions.NAVIGATE_TO -> {
          next.dispatch(action)
          render(activity, navigationStack)
        }

        Actions.NAVIGATE_BACK -> {
          if (navigationStack.frames.size == 1
              || navigationStack.frames.isEmpty()) {
            activity.finish()
          } else {
            next.dispatch(action)
            render(activity, navigationStack)
          }
        }

        else -> next.dispatch(action)
      }
    }
  }
}
