package com.hopskipnfall

import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

const val LOG_DEBUG = false

/** Current time as a duration relative to the start of the simulation. */
var now = 0.nanoseconds
val timeStep = 10.microseconds
val singleFrameDuration = 1.seconds / 60

fun main() {
  val clients =
    listOf(
      Client(id = 0, frameDelay = 1, pingRange = 13.milliseconds..16.milliseconds),
      Client(id = 1, frameDelay = 1, pingRange = 8.milliseconds..24.milliseconds),
      Client(id = 2, frameDelay = 2, pingRange = 6.milliseconds..25.milliseconds),
      Client(id = 3, frameDelay = 2, pingRange = 30.milliseconds..50.milliseconds),
    )
  val server = Server(clients)
  for (client in clients) {
    client.server = server
    client.siblings = clients.filter { it.id != client.id }
  }

  while (now <= 1.minutes) {
    server.run()
    for (it in clients) it.run()

    now += timeStep
  }
  server.lagstat()

  check(clients.all { it.isHealthy }) {
    "One or more clients is unhealthy! A deadlock likely occurred."
  }
}

/** Frame data that needs to be synchronized between clients in order to move forward. */
data class FrameData(val frameNumber: Long, val fromClientId: Int)

/** Simulates a [FrameData] packet in flight. */
data class DelayedPacket(val arrivalTime: Duration, val frameData: List<FrameData>) {
  fun hasArrived(): Boolean = now >= arrivalTime
}
