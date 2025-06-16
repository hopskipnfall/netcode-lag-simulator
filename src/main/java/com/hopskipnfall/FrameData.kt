package com.hopskipnfall

/** Frame data that needs to be synchronized between clients in order to move forward. */
data class FrameData(val frameNumber: Long, val fromClientId: Int)
