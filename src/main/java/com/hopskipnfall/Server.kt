package com.hopskipnfall

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

data class Server(val clients: List<Client>) {
  private val clientIds: Set<Int> = clients.map { it.id }.toSet()

  /** Data the server tracks about the client. */
  private val gameData =
    object {
      var lagLeeway: Duration = SINGLE_FRAME_DURATION
      var totalDrift: Duration = Duration.ZERO
    }

  /** A holding place for packets that are "in the air." */
  val incomingPackets = mutableListOf<DelayedPacket>()

  private var lastFanOutTime: Duration? = null

  /**
   * [FrameData] that has been received.
   *
   * When all clients' data from the same frame is captured, they will be removed and sent to each
   * client.
   */
  private val waitingPacketData = mutableSetOf<FrameData>()

  fun run(now: Duration, diagramBuilder: DiagramBuilder, frameDriftLogger: TimeBasedDataLogger) {
    val arrivedPackets = incomingPackets.findAndRemoveAll { it.hasArrived(now) }
    for (packet in arrivedPackets) {
      val frameData = packet.frameData.single()
      log("Received packet $frameData", now, debug = true)
      waitingPacketData += frameData
      val client = clients.first { it.id == frameData.fromClientId }
      client.serverData.receivedDataAt = now
    }

    val sentDataForFrames = mutableSetOf<Long>()
    for ((frameNumber, heldData) in waitingPacketData.groupBy { it.frameNumber }) {
      if (
        frameNumber < clients.maxOf { it.frameDelay } ||
          heldData.map { it.fromClientId }.toSet() == clientIds
      ) {

        log("Received data for all clients on frame $frameNumber. fanning out.", now, debug = true)
        clients.forEach { client ->
          val packet =
            DelayedPacket(arrivalTime = now + (client.pingRange.random() / 2), frameData = heldData)
          client.incomingPackets += packet

          diagramBuilder.registerPacketToClient(now, client = client.id, packet)

          if (lastFanOutTime != null) {
            // Calculate lag.
            val elapsedSinceReceivingFrameData = now - client.serverData.receivedDataAt
            val delaySinceLastFanOutMinusWaiting =
              now - lastFanOutTime!! - elapsedSinceReceivingFrameData
            val leewayChange = SINGLE_FRAME_DURATION - delaySinceLastFanOutMinusWaiting
            client.serverData.lagLeeway += leewayChange
            if (client.serverData.lagLeeway < Duration.ZERO) {
              // Lag leeway fell below zero. We caused lag!
              client.serverData.totalDrift += client.serverData.lagLeeway
              client.serverData.lagLeeway = Duration.ZERO
            } else if (client.serverData.lagLeeway > SINGLE_FRAME_DURATION) {
              client.serverData.lagLeeway = SINGLE_FRAME_DURATION
            }
          }
          frameDriftLogger.addRow(
            now,
            "Client" to "Client ${client.id}",
            "Induced gameplay drift" to client.serverData.totalDrift.toMillisDouble(),
          )
          diagramBuilder.registerServerWait(
            client.serverData.receivedDataAt,
            now,
            frameNumber = packet.frameData.first().frameNumber,
          )
        }
        if (lastFanOutTime != null) {
          gameData.lagLeeway += SINGLE_FRAME_DURATION - (now - lastFanOutTime!!)
          if (gameData.lagLeeway < Duration.ZERO) {
            // Lag leeway fell below zero. The game experienced lag!
            gameData.totalDrift += gameData.lagLeeway
            gameData.lagLeeway = Duration.ZERO
          } else if (gameData.lagLeeway > SINGLE_FRAME_DURATION) {
            gameData.lagLeeway = SINGLE_FRAME_DURATION
          }
        }

        lastFanOutTime = now
        sentDataForFrames.add(frameNumber)

        frameDriftLogger.addRow(
          now,
          "Client" to "Game (inferred)",
          "Induced gameplay drift" to gameData.totalDrift.toMillisDouble(),
        )
      }
    }
    waitingPacketData.removeAll { it.frameNumber in sentDataForFrames }
  }

  fun lagstat(now: Duration) {
    log("laggy: ${gameIsLaggy(now)}", now)
    log(
      "Lagstat:\n" +
        clients.joinToString(separator = "\n") {
          "${it.id} - Drift: ${it.serverData.totalDrift.toString(DurationUnit.MILLISECONDS)}"
        },
      now
    )
    log("Overall game drift: ${gameData.totalDrift}", now)
    log(
      "Sum of client lags: " +
        clients.sumOf { it.serverData.totalDrift.toMillisDouble() }.milliseconds,
      now
    )
    log(
      "Client-perceived lag:\n" +
        clients.joinToString(separator = "\n") { "${it.id} - ${it.clientPerceivedLag}" },
      now
    )
  }

  fun gameIsLaggy(now: Duration): Boolean =
    gameData.totalDrift.absoluteValue > (SINGLE_FRAME_DURATION * 30) * (now / 1.minutes)

  private fun log(s: String, now: Duration, debug: Boolean = false) {
    logWithTime("Server: $s", debug, now)
  }
}
