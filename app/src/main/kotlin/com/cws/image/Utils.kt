package com.cws.image

import android.app.Activity
import android.view.WindowManager
import com.github.andrewoma.dexx.kollection.ImmutableList
import com.github.andrewoma.dexx.kollection.immutableListOf
import com.github.andrewoma.dexx.kollection.toImmutableList

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

fun requestKeepScreenOn(activity: Activity) {
  activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
