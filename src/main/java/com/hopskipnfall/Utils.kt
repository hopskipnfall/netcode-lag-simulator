package com.hopskipnfall

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

class TimeBasedDataLogger(
  private val timestampTransformer: (Duration) -> Double,
  private val timeName: String = "Timstamp (ms)",
  private val precision: Duration = Duration.ZERO
) {
  private val times = mutableListOf<Double>()
  private val data = mutableMapOf<String, MutableList<Any>>()

  private var lastTime: Duration? = null

  fun log(time: Duration, vararg columnToValues: Pair<String, Any>) {
    columnToValues.forEach { (colName, _) ->
      if (data[colName] == null) data[colName] = mutableListOf()
    }

    if (
      lastTime == time &&
        columnToValues.all { (colName, _) ->
          data[colName]?.let { it.size == times.size - 1 } == true
        }
    ) {
      // This is more data for the same time step. Don't add a new time.
    } else {
      lastTime?.let { if (time - it < precision) return }
      times.add(timestampTransformer(time))
    }

    lastTime = time

    for ((column: String, value) in columnToValues) {
      var columnData = data[column]
      if (columnData == null) {
        columnData = mutableListOf()
        data[column] = columnData
      }
      columnData.add(value)
    }
  }

  fun buildDataFrame() =
    dataFrameOf(
      timeName to times,
      *(data.map { (columnName, values) -> columnName to values }.toTypedArray())
    )
}

interface Distribution {
  fun random(): Duration
}

data class NormalDistribution(val mean: Duration, val stdev: Duration) : Distribution {
  override fun random(): Duration =
    maxOf(
      java.util
        .Random()
        .nextGaussian(
          /* mean= */ mean.inWholeNanoseconds.toDouble(),
          /* stddev= */ stdev.inWholeNanoseconds.toDouble()
        )
        .nanoseconds,
      Duration.ZERO
    )
}

data class EqualDistribution(val range: ClosedRange<Duration>) : Distribution {
  override fun random(): Duration =
    if (range.start == range.endInclusive) {
      range.start
    } else {
      Random.nextLong(range.start.inWholeNanoseconds, range.endInclusive.inWholeNanoseconds)
        .nanoseconds
    }
}

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

operator fun ClosedRange<Duration>.div(int: Int): ClosedRange<Duration> =
  (this.start / int)..(this.endInclusive / int)
