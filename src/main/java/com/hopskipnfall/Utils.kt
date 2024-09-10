package com.hopskipnfall

import kotlin.math.*
import kotlin.random.asJavaRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

interface Distribution {
  fun random(): Duration
}

fun Duration.toMillisDouble(): Double =
  this.inWholeNanoseconds / 1.milliseconds.inWholeNanoseconds.toDouble()

fun Duration.toSecondsDouble(): Double =
  this.inWholeNanoseconds / 1.seconds.inWholeNanoseconds.toDouble()

data class LognormalDistribution(val mean: Duration, val stdev: Duration) : Distribution {

  override fun random(): Duration {
    val mean = mean.toMillisDouble()
    val stdev = stdev.toMillisDouble()

    val variance = stdev * stdev
    val sigmaSquared = ln((variance / (mean * mean)) + 1)
    val mu = ln(mean) - 0.5 * sigmaSquared
    val sigma = sqrt(sigmaSquared)

    fun generateNormal(mu: Double, sigma: Double): Double {
      val u1 = random.nextDouble()
      val u2 = random.nextDouble()
      val z = sqrt(-2 * ln(u1)) * cos(2 * PI * u2)
      return mu + sigma * z
    }

    val normalRandomNumber = generateNormal(mu, sigma)
    val lognormalRandomNumber = exp(normalRandomNumber)
    return lognormalRandomNumber.milliseconds
  }
}

data class NormalDistribution(val mean: Duration, val stdev: Duration) : Distribution {
  override fun random(): Duration =
    maxOf(
      random
        .asJavaRandom()
        .nextGaussian(/* mean= */ mean.toMillisDouble(), /* stddev= */ stdev.toMillisDouble())
        .milliseconds,
      Duration.ZERO
    )
}

data class EqualDistribution(val range: ClosedRange<Duration>) : Distribution {
  override fun random(): Duration =
    if (range.start == range.endInclusive) {
      range.start
    } else {
      random
        .nextLong(range.start.inWholeNanoseconds, range.endInclusive.inWholeNanoseconds)
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
