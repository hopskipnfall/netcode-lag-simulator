package com.hopskipnfall

import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class Client(
  val id: Int,
  val frameDelay: Int,
  val pingRange: Distribution,
  val description: String? = null,
) {
  lateinit var server: Server
  lateinit var siblings: List<Client>

  /** A holding place for packets that are "in the air." */
  val incomingPackets = mutableListOf<DelayedPacket>()

  /** When the current frame started. */
  private var newFrameTimestamp: Duration = 0.milliseconds

  private var frameNumber = 0L

  private val firstFrameToSendFrom: Int by lazy {
    max((siblings.maxOfOrNull { it.frameDelay } ?: 0) - frameDelay, 0)
  }

  private var lastFrameDataFrameNumberSent: Long? = null

  private var init = true

  private var timeReceivedDataNecessaryForNextFrame: Duration? = null

  fun run(
    now: Duration,
    frameNumberLogger: TimeBasedDataLogger,
    diagramBuilder: DiagramBuilder,
    objectiveLagLogger: TimeBasedDataLogger,
    timeStep: Duration,
  ) {
    // This block ensures that all of the clients are synchronized with respect to the server.
    // Different frame differences require different respective start times.
    val minFrameDelay = min(frameDelay, siblings.minOfOrNull { it.frameDelay } ?: frameDelay)
    val waitForFrames: Double = max((frameDelay - minFrameDelay).toDouble(), 0.0) / 2.0
    if (now < SINGLE_FRAME_DURATION * waitForFrames) {
      return
    } else if (init) {
      newFrameTimestamp = now
      init = false
    }

    frameNumberLogger.addRow(now, "Frame Number" to frameNumber, "Client" to "Client $id")
    if (now == newFrameTimestamp) {
      // This is the first loop!

      if (lastFrameDataFrameNumberSent == null && frameNumber >= firstFrameToSendFrom) {
        sendPacketToServer(now, diagramBuilder)
      }
    }

    if (now >= newFrameTimestamp + SINGLE_FRAME_DURATION) {
      // We can send inputs to the server and/or move forward in time if we are ready to do so.

      // Do we have the necessary data to move forward one frame?
      // Everybody will first synchronize on frame 0 + the maximum frame delay.
      val needSiblingsFrameData: Boolean =
        frameNumber >= max(siblings.maxOfOrNull { it.frameDelay } ?: 0, frameDelay) - 1

      var frameDataRequirementSatisfied = false
      if (needSiblingsFrameData) {
        val arrivedPackets =
          incomingPackets.findAndRemoveAll {
            it.hasArrived(now) && it.frameData.first().frameNumber == frameNumber + 1
          }

        if (arrivedPackets.isNotEmpty()) {
          check(
            arrivedPackets.size == 1
          ) // THIS MIGHT NOT ACTUALLY BE IMPOSSIBLE FOR IT TO BE GREATER THAN 1
          frameDataRequirementSatisfied = true
          timeReceivedDataNecessaryForNextFrame = arrivedPackets.single().arrivalTime
          log("Received packet: ${arrivedPackets.single()}.", debug = true, now)
        }
      } else {
        frameDataRequirementSatisfied = true
      }

      if (frameDataRequirementSatisfied) {
        log("moving to next frame", debug = true, now)
        frameNumber++
        if (
          timeReceivedDataNecessaryForNextFrame != null &&
            now > timeReceivedDataNecessaryForNextFrame!!
        ) {
          diagramBuilder.registerClientWaitForFrame(
            start = timeReceivedDataNecessaryForNextFrame!!,
            end = now,
            client = id,
            frameNumber = frameNumber,
          )
        }
        diagramBuilder.registerNewFrame(now, client = id, frameNumber)
        if (timeReceivedDataNecessaryForNextFrame != null) {
          objectiveLagLogger.addRow(
            now,
            "Frame Number" to frameNumber,
            "Client" to "Client $id" + if (description == null) "" else " ($description)",
            "Objective lag in a single frame (ms)" to
              max((now - newFrameTimestamp - SINGLE_FRAME_DURATION).toMillisDouble(), 0.0),
          )
        }

        timeReceivedDataNecessaryForNextFrame = null
        val lag = now - (newFrameTimestamp) - SINGLE_FRAME_DURATION
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
      } else {
        // The frame is lagged!
      }

      // Do we need to send new data?
      val lastFrameNumberSent = lastFrameDataFrameNumberSent
      if (
        frameNumber >= firstFrameToSendFrom &&
          (lastFrameNumberSent == null || lastFrameNumberSent < frameNumber + frameDelay)
      ) {
        sendPacketToServer(now, diagramBuilder)
      }
    }
  }

  fun isHealthy(now: Duration): Boolean = now - newFrameTimestamp < SINGLE_FRAME_DURATION * 10

  private fun sendPacketToServer(now: Duration, diagramBuilder: DiagramBuilder) {
    val packet =
      DelayedPacket(
        arrivalTime = now + (pingRange.random() / 2),
        listOf(FrameData(frameNumber + frameDelay, fromClientId = id)),
      )
    diagramBuilder.registerPacketToServer(now, client = id, packet)
    server.incomingPackets += packet
    lastFrameDataFrameNumberSent = packet.frameData.single().frameNumber
    log("Sending to server: $packet", debug = true, now)
  }

  private fun log(s: String, debug: Boolean = false, now: Duration) {
    logWithTime("Client $id (frame $frameNumber): $s", debug, now)
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
    var lagLeeway: Duration = SINGLE_FRAME_DURATION,
    var totalDrift: Duration = Duration.ZERO,
    var receivedDataAt: Duration = Duration.ZERO,
  )

  val serverData = ServerData()
}
