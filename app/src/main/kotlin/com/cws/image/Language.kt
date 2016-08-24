package com.cws.image

import trikita.jedux.Action

fun reduceLanguage(action: Action<Actions, *>, state: String): String {
  return when(action.type) {
    Actions.SET_LANGUAGE -> {
      val language = action.value as String
      language
    }

    else -> state
  }
}

fun setLanguage(language: String): Action<Actions, String> {
  return Action(Actions.SET_LANGUAGE,
                language)
}