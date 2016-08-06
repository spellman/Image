package com.cws.image

import android.app.Activity
import android.app.Application
import android.content.Context
import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import trikita.anvil.Anvil
import trikita.anvil.RenderableView
import trikita.jedux.Action
import trikita.jedux.Logger
import trikita.jedux.Store

val languages = immutableListOf("english",
                                "spanish",
                                "tagalog",
                                "french",
                                "german",
                                "russian",
                                "ethiopian")

val instructions =
    immutableListOf(
        immutableListOf("chest", "english"),
        immutableListOf("leg", "english"),
        immutableListOf("arm", "english"),
        immutableListOf("chest", "spanish"),
        immutableListOf("leg", "spanish"),
        immutableListOf("arm", "spanish"),
        immutableListOf("chest", "tagalog"),
        immutableListOf("leg", "tagalog"),
        immutableListOf("arm", "tagalog"),
        immutableListOf("chest", "french"),
        immutableListOf("leg", "french"),
        immutableListOf("arm", "french"),
        immutableListOf("chest", "german"),
        immutableListOf("leg", "german"),
        immutableListOf("arm", "german"),
        immutableListOf("chest", "russian"),
        immutableListOf("leg", "russian"),
        immutableListOf("arm", "russian"),
        immutableListOf("chest", "ethiopian"),
        immutableListOf("leg", "ethiopian"),
        immutableListOf("arm", "ethiopian")
    )

val instructionsBySubjectLanguagePair =
    immutableMapOf(
        Pair(immutableListOf("chest", "english"),
             Instruction(subject = "chest",
                         language = "english",
                         path = "chest/english/path")),
        Pair(immutableListOf("leg", "english"),
             Instruction(subject = "leg",
                         language = "english",
                         path = "leg/english/path")),
        Pair(immutableListOf("arm", "english"),
             Instruction(subject = "arm",
                         language = "english",
                         path = "arm/english/path")),
        Pair(immutableListOf("chest", "spanish"),
             Instruction(subject = "chest",
                         language = "spanish",
                         path = "chest/spanish/path")),
        Pair(immutableListOf("leg", "spanish"),
             Instruction(subject = "leg",
                         language = "spanish",
                         path = "leg/spanish/path")),
        Pair(immutableListOf("arm", "spanish"),
             Instruction(subject = "arm",
                         language = "spanish",
                         path = "arm/spanish/path")),
        Pair(immutableListOf("chest", "tagalog"),
             Instruction(subject = "chest",
                         language = "tagalog",
                         path = "chest/tagalog/path")),
        Pair(immutableListOf("leg", "tagalog"),
             Instruction(subject = "leg",
                         language = "tagalog",
                         path = "leg/tagalog/path")),
        Pair(immutableListOf("arm", "tagalog"),
             Instruction(subject = "arm",
                         language = "tagalog",
                         path = "arm/tagalog/path")),
        Pair(immutableListOf("chest", "french"),
             Instruction(subject = "chest",
                         language = "french",
                         path = "chest/french/path")),
        Pair(immutableListOf("leg", "french"),
             Instruction(subject = "leg",
                         language = "french",
                         path = "leg/french/path")),
        Pair(immutableListOf("arm", "french"),
             Instruction(subject = "arm",
                         language = "french",
                         path = "arm/french/path")),
        Pair(immutableListOf("chest", "german"),
             Instruction(subject = "chest",
                         language = "german",
                         path = "chest/german/path")),
        Pair(immutableListOf("leg", "german"),
             Instruction(subject = "leg",
                         language = "german",
                         path = "leg/german/path")),
        Pair(immutableListOf("arm", "german"),
             Instruction(subject = "arm",
                         language = "german",
                         path = "arm/german/path")),
        Pair(immutableListOf("chest", "russian"),
             Instruction(subject = "chest",
                         language = "russian",
                         path = "chest/russian/path")),
        Pair(immutableListOf("leg", "russian"),
             Instruction(subject = "leg",
                         language = "russian",
                         path = "leg/russian/path")),
        Pair(immutableListOf("arm", "russian"),
             Instruction(subject = "arm",
                         language = "russian",
                         path = "arm/russian/path")),
        Pair(immutableListOf("chest", "ethiopian"),
             Instruction(subject = "chest",
                         language = "ethiopian",
                         path = "chest/ethiopian/path")),
        Pair(immutableListOf("leg", "ethiopian"),
             Instruction(subject = "leg",
                         language = "ethiopian",
                         path = "leg/ethiopian/path")),
        Pair(immutableListOf("arm", "ethiopian"),
             Instruction(subject = "arm",
                         language = "ethiopian",
                         path = "arm/ethiopian/path"))
    )

data class Instruction(val subject: String, val language: String, val path: String)
data class State(val languages: ImmutableList<String>,
                 val language: String,
                 val instructions: ImmutableList<ImmutableList<String>>,
                 val instructionsBySubjectLanguagePair: ImmutableMap<ImmutableList<String>,
                     Instruction>,
                 val navigationStack: NavigationStack)

enum class Actions {
  INIT,
  SHOW_CURRENT_VIEW,
  NAVIGATE_TO,
  NAVIGATE_BACK,
  SET_LANGUAGE,
  PLAY_INSTRUCTION
}

data class NavigationActionValue(val value: Any?, val activity: Activity)

fun setLanguage(language: String): Action<Actions, String> {
  return Action(Actions.SET_LANGUAGE,
                language)
}

fun playInstruction(instruction: Instruction): Action<Actions, Instruction> {
  return Action(Actions.PLAY_INSTRUCTION,
                instruction)
}

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

fun reduceLanguage(state: String, action: Action<Actions, *>): String {
  return when(action.type) {
    Actions.SET_LANGUAGE -> {
      val language = action.value
      if (language is String) language
      else state
    }

    else -> state
  }
}

fun reduceNavigation(state: NavigationStack, action: Action<Actions, *>): NavigationStack {
  return when(action.type) {
    Actions.NAVIGATE_TO -> {
      val navigationFrame = action.value
      if (navigationFrame is NavigationFrame) state.push(navigationFrame)
      else state
    }

    Actions.NAVIGATE_BACK -> state.pop()

    else -> state
  }
}

class Reducer: Store.Reducer<Action<Actions, *>, State> {
  override fun reduce(action: Action<Actions, *>, state: State): State {
    return state.copy(language = reduceLanguage(state.language, action),
                      navigationStack = reduceNavigation(state.navigationStack, action))
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
    val v = makeView(frameToRender.scene,
                     frameToRender.props,
                     activity)
    activity.setContentView(v)
  }

  override fun dispatch(store: Store<Action<Actions, *>, State>,
                        action: Action<Actions, *>,
                        next: Store.NextDispatcher<Action<Actions, *>>) {
    when(action.type) {
      Actions.SHOW_CURRENT_VIEW -> {
        val value = action.value
        if (value is NavigationActionValue) {
          render(value.activity, store.state.navigationStack)
        }
      }

      Actions.NAVIGATE_TO -> {
        val value = action.value
        if (value is NavigationActionValue) {
          next.dispatch(Action(action.type, value.value))
          render(value.activity, store.state.navigationStack)
        }
      }

      Actions.NAVIGATE_BACK -> {
        val value = action.value
        if (value is NavigationActionValue) {
          if (store.state.navigationStack.frames.size == 1
              || store.state.navigationStack.frames.isEmpty()) {
            value.activity.finish()
          } else {
            println("ABOUT TO DISPATCH NAVIGATE_BACK TO REDUCER")
            next.dispatch(Action(action.type, value.value))
            render(value.activity, store.state.navigationStack)
          }
        }
      }

      else -> next.dispatch(action)
    }
  }
}

fun getInstruction(instructionsBySubjectLanguagePair: ImmutableMap<ImmutableList<String>, Instruction>,
                   k: ImmutableList<String>): Instruction? {
  return instructionsBySubjectLanguagePair[k]
}

fun getInstructions(instructionsBySubjectLanguagePair: ImmutableMap<ImmutableList<String>, Instruction>,
                    ks: ImmutableList<ImmutableList<String>>): ImmutableList<Instruction> {
  return ks.mapNotNull { getInstruction(instructionsBySubjectLanguagePair, it) }
           .toImmutableList()
}

fun getInstructions(state: State): ImmutableList<Instruction> {
  return getInstructions(state.instructionsBySubjectLanguagePair,
                         state.instructions)
}

fun getVisibleInstructions(instructions: ImmutableList<Instruction>, language: String): ImmutableList<Instruction> {
  return instructions.filter { it.language == language }.toImmutableList()
}

val initialLanguage = "english"

val initialState =
    State(languages = languages,
          language = initialLanguage,
          instructions = instructions,
          instructionsBySubjectLanguagePair = instructionsBySubjectLanguagePair,
          navigationStack = NavigationStack(
                              immutableListOf(
                                  NavigationFrame("main", null))))

class App : Application() {
  val store: Store<Action<Actions, *>, State> = Store(Reducer(),
                                                      initialState,
                                                      Logger("Image"),
                                                      Navigator())

  override fun onCreate() {
    super.onCreate()
    LeakCanary.install(this)
    Stetho.initializeWithDefaults(this)
    store.subscribe(Anvil::render)
  }
}




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
