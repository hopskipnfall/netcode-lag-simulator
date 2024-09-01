package com.hopskipnfall

import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Client(val id: Int, val frameDelay: Int, val pingRange: ClosedRange<Duration>) {
  lateinit var server: Server
  lateinit var siblings: List<Client>

  /** A holding place for packets that are "in the air." */
  val incomingPackets = mutableListOf<DelayedPacket>()

  /** When the current frame started. */
  private var newFrameTimestamp: Duration = now

  private var frameNumber = 0L

  private val firstFrameToSendFrom: Int by lazy {
    max((siblings.maxOfOrNull { it.frameDelay } ?: 0) - frameDelay, 0)
  }

  private var lastFrameDataFrameNumberSent: Long? = null

  fun run() {
    if (now == newFrameTimestamp) {
      // This is the first loop!

      if (lastFrameDataFrameNumberSent == null && frameNumber >= firstFrameToSendFrom) {
        val outPacket = buildPacketToServer()
        server.incomingPackets += outPacket
        lastFrameDataFrameNumberSent = outPacket.frameData.single().frameNumber
        log("FIRST FRAME EVER OUT PACKET $outPacket", debug = true)
      }
    }

    if (now >= newFrameTimestamp + singleFrameDuration) {
      // We can send inputs to the server and/or move forward in time if we are ready to do so.

      // Do we have the necessary data to move forward one frame?
      // Everybody will first synchronize on frame 0 + the maximum frame delay.
      val needSiblingsFrameData: Boolean =
        frameNumber >= max(siblings.maxOfOrNull { it.frameDelay } ?: 0, frameDelay) - 1

      var frameDataRequirementSatisfied = false
      if (needSiblingsFrameData) {
        val arrivedPackets =
          incomingPackets.findAndRemoveAll {
            it.hasArrived() && it.frameData.first().frameNumber == frameNumber + 1
          }

        if (arrivedPackets.isNotEmpty()) {
          check(
            arrivedPackets.size == 1
          ) // THIS MIGHT NOT ACTUALLY BE IMPOSSIBLE FOR IT TO BE GREATER THAN 1
          frameDataRequirementSatisfied = true
          log("Received packet: ${arrivedPackets.single()}.", debug = true)
        }
      } else {
        frameDataRequirementSatisfied = true
      }

      if (frameDataRequirementSatisfied) {
        log("moving to next frame", debug = true)
        frameNumber++
        val lag = now - (newFrameTimestamp) - singleFrameDuration
        if (lag <= timeStep) {
          // No lag.
        } else {
          clientPerceivedLag.totalLagSpikes++
          if (lag > 20.milliseconds) clientPerceivedLag.lagSpikesOver20Ms++
          else if (lag > 15.milliseconds) clientPerceivedLag.lagSpikesOver15Ms++
          else if (lag > 10.milliseconds) clientPerceivedLag.lagSpikesOver10Ms++
          else if (lag > 5.milliseconds) clientPerceivedLag.lagSpikesOver5Ms++
        }
        newFrameTimestamp = now
      }

      // Do we need to send new data?
      val lastFrameNumberSent = lastFrameDataFrameNumberSent
      if (
        frameNumber >= firstFrameToSendFrom &&
          (lastFrameNumberSent == null || lastFrameNumberSent < frameNumber + frameDelay)
      ) {
        val outPacket = buildPacketToServer()
        server.incomingPackets += outPacket
        lastFrameDataFrameNumberSent = outPacket.frameData.single().frameNumber
        log("SENDING OUT PACKET $outPacket", debug = true)
      }
    }
  }

  val isHealthy: Boolean
    get() = now - newFrameTimestamp < singleFrameDuration * 10

  private fun buildPacketToServer(frameNo: Long = frameNumber) =
    DelayedPacket(
      arrivalTime = now + randomDuration(pingRange / 2),
      listOf(FrameData(frameNo + frameDelay, fromClientId = id))
    )

  private fun log(s: String, debug: Boolean = false) {
    logWithTime("Client $id (frame $frameNumber): $s", debug)
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
    var lagLeeway: Duration = singleFrameDuration,
    var totalDrift: Duration = 0.seconds,
    var receivedDataAt: Duration = 0.seconds,
  )

  val serverData = ServerData()
}
