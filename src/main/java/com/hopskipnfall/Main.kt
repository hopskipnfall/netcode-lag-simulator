package com.hopskipnfall

import java.io.File
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.kotlinx.kandy.dsl.plot
import org.jetbrains.kotlinx.kandy.letsplot.export.save
import org.jetbrains.kotlinx.kandy.letsplot.export.toHTML
import org.jetbrains.kotlinx.kandy.letsplot.layers.line
import org.jetbrains.kotlinx.kandy.letsplot.layers.points

const val LOG_DEBUG = false

/** Current time as a duration relative to the start of the simulation. */
var now = 0.nanoseconds
val timeStep = 10.microseconds
val singleFrameDuration = 1.seconds / 60

val frameNumberLogger = TimeBasedDataLogger({ it.toMillisDouble() })
val frameDriftLogger = TimeBasedDataLogger({ it.toMillisDouble() })
val objectiveLagLogger = TimeBasedDataLogger({ it.toMillisDouble() })

// Some actual ping measurements I took.
val WIFI = LognormalDistribution(mean = 10.424.milliseconds, stdev = 8.193.milliseconds)
val WIRED = LognormalDistribution(mean = 6.731.milliseconds, stdev = 1.920.milliseconds)

// Use a fixed seed.
val random = Random(42L)

fun main() {
  val clients =
    listOf(
      Client(id = 0, frameDelay = 1, WIFI),
      Client(id = 1, frameDelay = 1, WIFI),
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
  check(clients.all { it.isHealthy }) {
    "One or more clients is unhealthy! A deadlock likely occurred."
  }
  server.lagstat()

  if (clients.size < 3) {
    diagramBuilder.draw()
  } else {
    println("Not drawing diagram, too many clients.")
  }

  //  val frameNumberPlot =
  //    frameNumberLogger.buildDataFrame().plot {
  //      line {
  //        x("Timstamp (ms)")
  //        y("Frame Number")
  //
  //        color("Client")
  //      }
  //    }
  //  frameNumberPlot.save("frameNumberPlot.png")
  //  File("lets-plot-images/frameNumberPlot.html").writeText(frameNumberPlot.toHTML())

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

  val lagPlot =
    objectiveLagLogger.buildDataFrame().plot {
      points {
        x("Timstamp (ms)")
        y("Lag")

        color("Client")
      }
    }
  lagPlot.save("lag.png")
  File("lets-plot-images/lag.html").writeText(lagPlot.toHTML())
}

/** Frame data that needs to be synchronized between clients in order to move forward. */
data class FrameData(val frameNumber: Long, val fromClientId: Int)

/** Simulates a [FrameData] packet in flight. */
data class DelayedPacket(val arrivalTime: Duration, val frameData: List<FrameData>) {
  fun hasArrived(): Boolean = now >= arrivalTime
}
