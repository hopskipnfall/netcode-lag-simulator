package com.hopskipnfall

import kotlin.time.Duration

/** Simulates a [FrameData] packet in flight. */
data class DelayedPacket(val arrivalTime: Duration, val frameData: List<FrameData>) {
  fun hasArrived(now: Duration): Boolean = now >= arrivalTime
}
