package com.hopskipnfall

import kotlin.time.Duration

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

    //    waitingPacketData
    //      .groupBy { it.fromClientId }
    //      .forEach { (fromClient, frameDataList) ->
    //        check(
    //          frameDataList
    //            .filter { it.frameNumber > clients.first { it.id == fromClient }.frameDelay }
    //            .size <= 1
    //        ) {
    //          // NOT EXACTLY TRUE i think.
    //          "CLIENT $fromClient: I think it's impossible for the server to have multiple sets of
    // user data..? $frameDataList"
    //        }
    //      }

    val sentDataForFrames = mutableSetOf<Long>()
    for ((frameNumber, heldData) in waitingPacketData.groupBy { it.frameNumber }) {
      if (
        frameNumber < clients.maxOf { it.frameDelay } ||
          heldData.map { it.fromClientId }.toSet() == clientIds
      ) {

        log("Received data for all clients on frame $frameNumber. fanning out.", debug = true)
        clients.forEach { client ->
          client.incomingPackets +=
            DelayedPacket(arrivalTime = now + (client.pingRange.random() / 2), frameData = heldData)

          if (lastFanOutTime != null) {
            // Calculate lag.
            val elapsedSinceReceivingFrameData = now - client.serverData.receivedDataAt
            val delaySinceLastFanOutMinusWaiting =
              now - lastFanOutTime!! - elapsedSinceReceivingFrameData

            val leewayChange = singleFrameDuration - delaySinceLastFanOutMinusWaiting
            client.serverData.lagLeeway += leewayChange
            if (client.serverData.lagLeeway < Duration.ZERO) {
              // Lag leeway fell below zero. We caused lag!
              client.serverData.totalDrift += leewayChange
              client.serverData.lagLeeway = Duration.ZERO
            }
          }
          frameDriftLogger.log(
            now,
            "Client" to "Client ${client.id}",
            "Induced gameplay drift" to client.serverData.totalDrift.inWholeMicroseconds / 1_000.0
          )
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
