package com.hopskipnfall

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.export.toHTML
import org.jetbrains.kotlinx.kandy.letsplot.layers.line

const val LOG_DEBUG = false

/** Current time as a duration relative to the start of the simulation. */
var now = 0.nanoseconds
val timeStep = 10.microseconds
val singleFrameDuration = 1.seconds / 60

val frameNumberLogger =
  TimeBasedDataLogger({ it.inWholeMicroseconds / 1_000.0 }, precision = 0.1.milliseconds)
val frameDriftLogger =
  TimeBasedDataLogger({ it.inWholeMicroseconds / 1_000.0 }, precision = 0.1.milliseconds)

// Some actual ping measurements I took.
val WIFI = NormalDistribution(mean = 10.424.milliseconds, stdev = 8.193.milliseconds)
val WIRED = NormalDistribution(mean = 6.731.milliseconds, stdev = 1.920.milliseconds)

fun main() {
  val clients =
    listOf(
      Client(id = 0, frameDelay = 1, WIFI),
      Client(id = 1, frameDelay = 1, WIRED),
      Client(id = 2, frameDelay = 1, EqualDistribution(6.milliseconds..15.milliseconds)),
      Client(id = 3, frameDelay = 1, EqualDistribution(10.milliseconds..11.milliseconds)),
    )
  val server = Server(clients)
  for (client in clients) {
    client.server = server
    client.siblings = clients.filter { it.id != client.id }
  }

  while (now <= 500.milliseconds) {
    server.run()
    for (it in clients) it.run()

    now += timeStep
  }
  check(clients.all { it.isHealthy }) {
    "One or more clients is unhealthy! A deadlock likely occurred."
  }
  server.lagstat()

  val frameNumberPlot =
    frameNumberLogger.buildDataFrame().plot {
      line {
        x("Timstamp (ms)")
        y("Frame Number")

        color("Client")
      }
    }
  frameNumberPlot.save("frameNumberPlot.png")
  File("lets-plot-images/frameNumberPlot.html").writeText(frameNumberPlot.toHTML())

  val frameDriftPlot =
    frameDriftLogger.buildDataFrame().plot {
      line {
        x("Timstamp (ms)")
        y("Induced gameplay drift")

        color("Client")
      }
    }
  frameDriftPlot.save("frameDriftPlot.png")
  File("lets-plot-images/frameDriftPlot.html").writeText(frameDriftPlot.toHTML())
}

/** Frame data that needs to be synchronized between clients in order to move forward. */
data class FrameData(val frameNumber: Long, val fromClientId: Int)

/** Simulates a [FrameData] packet in flight. */
data class DelayedPacket(val arrivalTime: Duration, val frameData: List<FrameData>) {
  fun hasArrived(): Boolean = now >= arrivalTime
}
