package com.cws.image

import trikita.jedux.Action

fun reduceInstruction(action: Action<Actions, *>, state: Instruction?): Instruction? {
  return when(action.type) {
    Actions.SET_INSTRUCTION -> {
      action.value as? Instruction
    }

    else -> state
  }
}

fun setInstruction(instruction: Instruction?): Action<Actions, Instruction?> {
  return Action(Actions.SET_INSTRUCTION, instruction)
}
