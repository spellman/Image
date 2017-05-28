package com.cws.image

import android.content.Context
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf

data class Validator(val message: String, val fn: (String, String) -> Boolean)

class ValidatorApplier(val validators: Collection<Validator>) {
  fun validate(password: String, passwordConfirmation: String): ImmutableList<String> {
    return validators.fold(immutableListOf<String>()) { accMessages, validator ->
      if (validator.fn(password, passwordConfirmation)) {
        accMessages
      }
      else {
        accMessages.plus(validator.message)
      }
    }
  }
}

fun validatePasswordWithConfirmation(context: Context): ValidatorApplier {
  val passwordMinLength = context.resources.getInteger(R.integer.password_min_length)

  val badLetterSequences =
    "abcdefghijklmnopqrstuvwxyz".partitionBy(8, 1).map{ cs -> cs.joinToString("") }

  val badNumberSequences =
    "123456789".partitionBy(5, 1).map{ cs -> cs.joinToString("") }
      .plus(immutableListOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0").map { s -> s.repeat(4) })

  val commonSequences =
    badLetterSequences
      .plus(badLetterSequences.map { s -> s.reversed() })
      .plus(badNumberSequences)
      .plus(badNumberSequences.map { s -> s.reversed() })

  return ValidatorApplier(
    immutableListOf(
      Validator(
        context.resources.getQuantityString(R.plurals.password_min_length, passwordMinLength, passwordMinLength),
        { password, _ -> password.length >= passwordMinLength }
      ),
      Validator(
        context.getString(R.string.password_cannot_contain_password),
        { password, _ -> !Regex("p[a4@][s5]{2}w[o0]rd").containsMatchIn(password) }
      ),
      Validator(
        context.getString(R.string.password_cannot_contain_common_sequence),
        { password, _ -> commonSequences.none { s -> password.contains(s) } }
      ),
      Validator(
        context.getString(R.string.passwords_must_match),
        { password, passwordConfirmation -> password == passwordConfirmation}
      )
    )
  )
}
