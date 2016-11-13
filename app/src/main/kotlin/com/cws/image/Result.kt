package com.cws.image

// 2016-10-19 Cort Spellman
// I made this generic Result by modifying the Result class here:
// https://github.com/kittinunf/Result/blob/master/result/src/main/kotlin/com/github/kittinunf/result/Result.kt
sealed class Result<out A, out B> {
  class Err<out A: Any, out B: Any>(val errValue: A) : Result<A, B>() {
    override fun toString() = "${this.javaClass.canonicalName} Err: ${errValue}"

    override fun hashCode(): Int = errValue.hashCode()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is Err<*, *> && errValue == other.errValue
    }
  }

  class Ok<out A: Any, out B: Any>(val okValue: B) : Result<A, B>() {
    override fun toString() = "${this.javaClass.canonicalName} Ok: ${okValue}"

    override fun hashCode(): Int = okValue.hashCode()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is Ok<*, *> && okValue == other.okValue
    }
  }
}

