package com.cws.image

import android.app.Activity
import android.view.WindowManager
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.github.andrewoma.dexx.kollection.toImmutableList
import timber.log.Timber

tailrec fun <T> _partitionBy(
  acc: ImmutableList<ImmutableList<T>>,
  s: ImmutableList<T>,
  n: Int,
  step: Int): ImmutableList<ImmutableList<T>> {
  if (s.isEmpty()) {
    return acc
  }
  else {
    val p = s.take(n)
    if (p.count() != n) {
      return acc
    }
    else {
      return _partitionBy(acc.plus(p), s.drop(step), n, step)
    }
  }
}

fun <T> Collection<T>.partitionBy(n: Int): ImmutableList<ImmutableList<T>> {
  return this.partitionBy(n, n)
}

fun <T> Collection<T>.partitionBy(n: Int, step: Int): ImmutableList<ImmutableList<T>> {
  return _partitionBy(immutableListOf(), this.toImmutableList(), n, step)
}

fun String.partitionBy(n: Int): ImmutableList<ImmutableList<Char>> {
  return this.toList().partitionBy(n)
}

fun String.partitionBy(n: Int, step: Int): ImmutableList<ImmutableList<Char>> {
  return this.toList().partitionBy(n, step)
}

fun <T> isAllSame(coll: Collection<T>): Boolean {
  if (coll.isEmpty()) { return true }

  val h = coll.first()
  return coll.drop(1).all { x -> x == h }
}

fun isSequential(coll: Iterable<Char>): Boolean {
  if (coll.count() in 0..1) { return true }

  val first = coll.first()
  val last = coll.last()
  if (coll.all { c -> c.isDigit() }) {
    return isSequentialDigits(coll.toImmutableList())
  }

  val (stepFn, backStepFn) =
    if (first < last) {
      Pair(Char::inc, Char::dec)
    }
    else {
      Pair(Char::dec, Char::inc)
    }

  val stepped = coll.map { c -> stepFn(c) }
  val backStepped = coll.map { c -> backStepFn(c) }
  return coll.drop(1) == stepped.dropLast(1)
         && coll.toList().dropLast(1) == backStepped.drop(1)
}

fun isSequentialDigits(coll: Iterable<Char>): Boolean {
  // Digits are considered to wrap around at 0.
  // Ex: 8, 9, 0, 1 is sequential
  // Ex: 2, 1, 0, 9 is sequential
  val numbers = immutableListOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
  val n = coll.count()
  val seqsIncreasing = numbers.plus(numbers.take(n)).partitionBy(n, 1)
  val seqsDecreasing = seqsIncreasing.map { s -> s.reversed()}
  return seqsIncreasing.plus(seqsDecreasing).any { s -> coll == s }
}

fun requestKeepScreenOn(activity: Activity) {
  activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
