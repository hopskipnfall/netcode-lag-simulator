package com.hopskipnfall

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

private var gameDrift = Duration.ZERO

data class Server(val clients: List<Client>) {
  private val clientIds: Set<Int> = clients.map { it.id }.toSet()

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

  fun run() {
    val arrivedPackets = incomingPackets.findAndRemoveAll { it.hasArrived() }
    for (packet in arrivedPackets) {
      val frameData = packet.frameData.single()
      log("Received packet $frameData", debug = true)
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

        log("Received data for all clients on frame $frameNumber. fanning out.", debug = true)
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
            val leewayChange = singleFrameDuration - delaySinceLastFanOutMinusWaiting
            client.serverData.lagLeeway += leewayChange
            if (client.serverData.lagLeeway < Duration.ZERO) {
              // Lag leeway fell below zero. We caused lag!
              client.serverData.totalDrift += client.serverData.lagLeeway
              client.serverData.lagLeeway = Duration.ZERO
            } else if (client.serverData.lagLeeway > singleFrameDuration) {
              client.serverData.lagLeeway = singleFrameDuration
            }
          }
          diagramBuilder.registerServerWait(
            client.serverData.receivedDataAt,
            client = client.id,
            now,
            frameNumber = packet.frameData.first().frameNumber,
          )
          frameDriftLogger.addRow(
            now,
            "Client" to "Client ${client.id}",
            "Induced gameplay drift" to client.serverData.totalDrift.toMillisDouble(),
          )
        }
        if (lastFanOutTime != null) {
          gameDrift += singleFrameDuration - (now - lastFanOutTime!!)
        }

        lastFanOutTime = now
        sentDataForFrames.add(frameNumber)

        frameDriftLogger.addRow(
          now,
          "Client" to "Overall",
          "Induced gameplay drift" to gameDrift.toMillisDouble(),
        )
      }
    }
    waitingPacketData.removeAll { it.frameNumber in sentDataForFrames }
  }

  fun lagstat() {
    log("laggy: $gameIsLaggy")
    log(
      "Lagstat:\n" +
        clients.joinToString(separator = "\n") {
          "${it.id} - Drift: ${it.serverData.totalDrift.toString(DurationUnit.MILLISECONDS)}"
        }
    )
    log("Overall game drift: $gameDrift")
    log(
      "Sum of client lags: " +
        clients.sumOf { it.serverData.totalDrift.toMillisDouble() }.milliseconds
    )
    log(
      "Client-perceived lag:\n" +
        clients.joinToString(separator = "\n") { "${it.id} - ${it.clientPerceivedLag}" }
    )
  }

  val gameIsLaggy: Boolean
    get() = gameDrift.absoluteValue > (singleFrameDuration * 30) * (now / 1.minutes)

  private fun log(s: String, debug: Boolean = false) {
    logWithTime("Server: $s", debug)
  }
}
