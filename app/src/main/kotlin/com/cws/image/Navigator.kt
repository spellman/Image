package com.cws.image

import com.brianegan.bansa.Middleware
import com.github.andrewoma.dexx.kollection.ImmutableList

sealed class Scene {
  class Main() : Scene() {
    override fun toString(): String { return this.javaClass.canonicalName.split(".").last() }
  }

  class Instruction() : Scene() {
    override fun toString(): String { return this.javaClass.canonicalName.split(".").last() }
  }
}

data class NavigationStack(val scenes: ImmutableList<Scene>) {
  fun push(scene: Scene): NavigationStack {
    return this.copy(scenes = scenes.plus(scene))
  }

  fun pop(): NavigationStack {
    return this.copy(scenes = scenes.dropLast(1))
  }

  fun peek(): Scene {
    return scenes.last()
  }

  fun size(): Int {
    return scenes.size
  }
}

val navigator = Middleware<State> { store, action, next ->
  when (action) {
    is Action.NavigateBack -> {
      if (store.state.navigationStack.size() > 1) { next.dispatch(action) }
      else { action.activity.finish() }
    }

    else -> next.dispatch(action)
  }
}
