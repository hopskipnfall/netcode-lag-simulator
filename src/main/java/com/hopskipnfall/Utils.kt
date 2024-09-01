package com.hopskipnfall

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit

fun logWithTime(s: String, debug: Boolean = false) {
  if (debug && !LOG_DEBUG) return
  println("${now.toString(DurationUnit.MILLISECONDS, decimals = 0)} $s")
}

/** Removes all matching entities from a [MutableList] and returns them. */
fun <T> MutableList<T>.findAndRemoveAll(predicate: (T) -> Boolean): List<T> {
  val removed = mutableListOf<T>()
  this.removeAll { entry ->
    val matched = predicate(entry)
    if (matched) removed.add(entry)
    matched
  }
  return removed
}

fun randomDuration(range: ClosedRange<Duration>) =
  if (range.start == range.endInclusive) {
    range.start
  } else {
    Random.nextLong(range.start.inWholeNanoseconds, range.endInclusive.inWholeNanoseconds)
      .nanoseconds
  }

operator fun ClosedRange<Duration>.div(int: Int): ClosedRange<Duration> =
  (this.start / int)..(this.endInclusive / int)
