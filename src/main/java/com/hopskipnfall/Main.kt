package com.hopskipnfall

import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

const val LOG_DEBUG = false

/** Current time as a duration relative to the start of the simulation. */
var now = 0.nanoseconds
val timeStep = 100.microseconds
val singleFrameDuration = 1.seconds / 60

fun main() {
  val clients =
    listOf(
      Client(id = 1, pingRange = 6.milliseconds..19.milliseconds),
      Client(id = 2, pingRange = 5.milliseconds..16.milliseconds),
      Client(id = 3, pingRange = 6.milliseconds..13.milliseconds),
      Client(id = 4, pingRange = 5.milliseconds..12.milliseconds),
    )
  val server = Server(clients)
  for (it in clients) it.server = server

  while (now <= 3.minutes) {
    now += timeStep

    server.run()
    for (it in clients) it.run()
  }
  server.lagstat()
}

/** Frame data that needs to be synchronized between clients in order to move forward. */
data class FrameData(val frameNumber: Long, val fromClientId: Int)

/** Simulates a [FrameData] packet in flight. */
data class DelayedPacket(val arrivalTime: Duration, val frameData: List<FrameData>) {
  fun hasArrived(): Boolean = now >= arrivalTime
}

class Client(val id: Int, val pingRange: ClosedRange<Duration>) {
  lateinit var server: Server

  /** A holding place for packets that are "in the air." */
  val incomingPackets = mutableListOf<DelayedPacket>()

  /** When the current frame started. */
  private var newFrameTimestamp = now

  private var frameNumber = 0L

  private var hasDataNecessaryForNextFrame = false

  private var initialState = true

  fun run() {
    if (initialState) {
      log("Initial state! sending packet to server", debug = true)
      server.incomingPackets += buildPacketToServer()
      initialState = false
    }

    val arrivedPackets = incomingPackets.findAndRemoveAll { it.hasArrived() }
    if (arrivedPackets.isNotEmpty()) {
      check(arrivedPackets.size == 1)

      log(
        "received packet for frame ${arrivedPackets.single().frameData.first().frameNumber}",
        debug = true
      )
      hasDataNecessaryForNextFrame = true
    }

    if (hasDataNecessaryForNextFrame && now >= newFrameTimestamp + singleFrameDuration) {
      log("moving to next frame, sending packet to server", debug = true)
      frameNumber++
      server.incomingPackets += buildPacketToServer()

      val lag = now - newFrameTimestamp - singleFrameDuration
      if (lag < timeStep) {
        // No lag.
      } else {
        clientPerceivedLag.totalLagSpikes++
        if (lag > 20.milliseconds) clientPerceivedLag.lagSpikesOver20Ms++
        else if (lag > 15.milliseconds) clientPerceivedLag.lagSpikesOver15Ms++
        else if (lag > 10.milliseconds) clientPerceivedLag.lagSpikesOver10Ms++
        else if (lag > 5.milliseconds) clientPerceivedLag.lagSpikesOver5Ms++
      }

      newFrameTimestamp = now
      hasDataNecessaryForNextFrame = false
    }
  }

  private fun buildPacketToServer() =
    DelayedPacket(
      arrivalTime = now + randomDuration(pingRange / 2),
      listOf(FrameData(frameNumber, fromClientId = id))
    )

  private fun log(s: String, debug: Boolean = false) {
    logWithTime("Client $id: $s", debug)
  }

  data class ClientPerceivedLag(
    var totalLagSpikes: Long = 0,
    var lagSpikesOver5Ms: Long = 0,
    var lagSpikesOver10Ms: Long = 0,
    var lagSpikesOver15Ms: Long = 0,
    var lagSpikesOver20Ms: Long = 0,
  )

  val clientPerceivedLag = ClientPerceivedLag()

  /** Data the server tracks about the client. */
  data class ServerData(
    var lagLeeway: Duration = 0.seconds,
    var totalDrift: Duration = 0.seconds,
    var receivedDataAt: Duration = 0.seconds,
  )

  val serverData = ServerData()
}

data class Server(val clients: List<Client>) {
  private val clientIds: Set<Int> = clients.map { it.id }.toSet()

  /** A holding place for packets that are "in the air." */
  val incomingPackets = mutableListOf<DelayedPacket>()

  private var lastFanOutTime = now

  /**
   * [FrameData] that has been received.
   *
   * When all clients' data from the same frame is captured, they will be removed and sent to each
   * client.
   */
  private val waitingPacketData = mutableSetOf<FrameData>()

  fun run() {
    val arrivedPackets = incomingPackets.findAndRemoveAll { it.hasArrived() }
    for (packet in arrivedPackets) {
      val frameData = packet.frameData.single()
      log("Received packet from client ${frameData.fromClientId}", debug = true)
      waitingPacketData += frameData
      val client = clients.first { it.id == frameData.fromClientId }
      client.serverData.receivedDataAt = now
    }

    waitingPacketData
      .groupBy { it.fromClientId }
      .forEach { (fromClient, frameDataList) ->
        check(frameDataList.size == 1) {
          "CLIENT $fromClient: I think it's impossible for the server to have multiple sets of user data..?"
        }
      }

    val sentDataForFrames = mutableSetOf<Long>()
    for ((frameNumber, heldData) in waitingPacketData.groupBy { it.frameNumber }) {
      if (heldData.map { it.fromClientId }.toSet() == clientIds) {
        log("Received data for all clients on frame $frameNumber. fanning out.", debug = true)
        clients.forEach { client ->
          client.incomingPackets +=
            DelayedPacket(
              arrivalTime = now + randomDuration(client.pingRange),
              frameData = heldData
            )

          // Calculate lag.
          val elapsedSinceReceivingFrameData = now - client.serverData.receivedDataAt
          val delaySinceLastFanOutMinusWaiting =
            now - lastFanOutTime - elapsedSinceReceivingFrameData

          val leewayChange = singleFrameDuration - delaySinceLastFanOutMinusWaiting
          client.serverData.lagLeeway += leewayChange
          if (client.serverData.lagLeeway < Duration.ZERO) {
            // Lag leeway fell below zero. We caused lag!

            client.serverData.totalDrift += leewayChange
            client.serverData.lagLeeway = Duration.ZERO
          }
        }
        lastFanOutTime = now
        sentDataForFrames.add(frameNumber)
      }
    }
    waitingPacketData.removeAll { it.frameNumber in sentDataForFrames }
  }

  fun lagstat() {
    log(
      "Lagstat:\n" +
        clients.joinToString(separator = "\n") { "${it.id} - Drift: ${it.serverData.totalDrift}" }
    )
    log(
      "Client-perceived lag:\n" +
        clients.joinToString(separator = "\n") { "${it.id} - ${it.clientPerceivedLag}" }
    )
  }

  private fun log(s: String, debug: Boolean = false) {
    logWithTime("Server: $s", debug)
  }
}

fun logWithTime(s: String, debug: Boolean = false) {
  if (debug && !LOG_DEBUG) return
  println("${now.toString(DurationUnit.SECONDS, decimals = 2)} $s")
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
