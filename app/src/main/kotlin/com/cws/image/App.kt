package com.cws.image

import android.app.Application
import com.facebook.stetho.Stetho
import com.github.andrewoma.dexx.kollection.*
import com.squareup.leakcanary.LeakCanary
import trikita.anvil.Anvil
import trikita.jedux.Action
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
data class State(val language: String,
                 val instructions: ImmutableList<ImmutableList<String>>,
                 val instructionsBySubjectLanguagePair: ImmutableMap<ImmutableList<String>,
                     Instruction>)

enum class Actions {
  INIT,
  SET_LANGUAGE,
  PLAY_INSTRUCTION
}

fun setLanguage(language: String): Action<Actions, String> {
  return Action(Actions.SET_LANGUAGE,
                language)
}

fun playInstruction(instruction: Instruction): Action<Actions, Instruction> {
  return Action(Actions.PLAY_INSTRUCTION,
                instruction)
}

class Reducer : Store.Reducer<Action<Actions, *>, State> {
  override fun reduce(action: Action<Actions, *>, state: State): State {
    return when(action.type) {
      Actions.SET_LANGUAGE -> {
        val language = action.value
        if (language !is String) state
        else state.copy(language = language)
      }
      else -> state
    }
  }
}

fun getInstruction(instructionsBySubjectLanguagePair: ImmutableMap<ImmutableList<String>, Instruction>,
                   k: ImmutableList<String>): Instruction? {
  return instructionsBySubjectLanguagePair[k]
}

fun getInstructions(instructionsBySubjectLanguagePair: ImmutableMap<ImmutableList<String>, Instruction>,
                    ks: ImmutableList<ImmutableList<String>>): ImmutableList<Instruction?> {
  return ks.map { getInstruction(instructionsBySubjectLanguagePair, it) }
           .toImmutableList()
}

fun getInstructions(state: State): ImmutableList<Instruction?> {
  return getInstructions(state.instructionsBySubjectLanguagePair,
                         state.instructions)
}

fun getVisibleInstructions(instructions: ImmutableList<Instruction?>, language: String): ImmutableList<Instruction> {
  return instructions.filter { it?.language == language }.filterNotNull().toImmutableList()
}

val initialState = State("english",
                         instructions,
                         instructionsBySubjectLanguagePair)

val store: Store<Action<Actions, *>, State> = Store(Reducer(), initialState)

class App : Application() {
  override fun onCreate() {
    super.onCreate()
    LeakCanary.install(this);
    Stetho.initializeWithDefaults(this)
    store.subscribe(Anvil::render)
  }
}
