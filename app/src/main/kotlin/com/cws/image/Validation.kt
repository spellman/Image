package com.cws.image

import android.content.Context
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.github.andrewoma.dexx.kollection.toImmutableList

data class Validator <in A> (val message: String, val fn: (A) -> Boolean)

class ValidatorApplier <in A> (vararg val validators: Validator<A>) {
  fun validate(arg: A): ImmutableList<String> {
    return validators.fold(immutableListOf<String>()) { accMessages, validator ->
      if (validator.fn(arg)) {
        accMessages
      }
      else {
        accMessages.plus(validator.message)
      }
    }
  }
}

fun makePasswordValidator(context: Context): ValidatorApplier<String> {
  val passwordMinLength = context.resources.getInteger(R.integer.password_min_length)

  return ValidatorApplier(
    Validator(
      context.resources.getQuantityString(R.plurals.password_min_length, passwordMinLength, passwordMinLength),
      { password -> password.length >= passwordMinLength }
    ),
    Validator(
      context.getString(R.string.password_cannot_contain_password),
      { password -> !Regex("p[a4@][s5]{2}w[o0]rd").containsMatchIn(password) }
    ),
    Validator(
      context.getString(R.string.password_cannot_contain_common_sequence),
      { password ->
        password.toCharArray().toList().toImmutableList().partitionBy(4, 1)
          .none { chars -> isAllSame(chars) || isSequential(chars) }
      }
    )
  )
}

fun makePasswordConfirmationValidator(context: Context): ValidatorApplier<Pair<String, String>> {
  return ValidatorApplier(
    Validator(
      context.getString(R.string.passwords_must_match),
      { (password, passwordConfirmation) -> password == passwordConfirmation}
    )
  )
}
