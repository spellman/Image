package com.cws.image

// 2016-10-19 Cort Spellman
// I made this generic Result by modifying the Result class here:
// https://github.com/kittinunf/Result/blob/master/result/src/main/kotlin/com/github/kittinunf/result/Result.kt
sealed class Result<out A, out B> {
  data class Err<out A: Any, out B: Any>(val errValue: A) : Result<A, B>()
  data class Ok<out A: Any, out B: Any>(val okValue: B) : Result<A, B>()
}
