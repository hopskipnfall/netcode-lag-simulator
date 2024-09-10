package com.hopskipnfall

import com.github.nwillc.ksvg.RenderMode
import com.github.nwillc.ksvg.elements.SVG
import java.io.FileWriter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration

val diagramBuilder = DiagramBuilder()

// These helper functions prepare the number for SVG.
private fun Int.str() = this.toString()

private fun Long.str() = this.toString()

private fun Double.str() = this.roundToInt().toString()

class DiagramBuilder(private val heightPx: Int = 450, private val framesToDraw: Int = 5) {
  private val clientsDoneTracking = mutableSetOf<Int>()

  private val leftMargin: Int =
    ((singleFrameDuration * 1.5).toMillisDouble() * PIXELS_PER_MILLISECOND).roundToInt()
  private val svgActions = mutableListOf<SVG.() -> Unit>()

  private var maxX = 0

  private fun timeToX(time: Duration): Int =
    (leftMargin + time.toMillisDouble() * PIXELS_PER_MILLISECOND).roundToInt()

  fun registerPacketToServer(now: Duration, client: Int, delayedPacket: DelayedPacket) {
    if (client in clientsDoneTracking) return

    val x = timeToX(delayedPacket.arrivalTime)
    maxX = max(x, maxX)
    svgActions.add {
      line {
        x1 = timeToX(now).str()
        x2 = x.str()
        y1 = yCoordOfClientId(client).str()
        y2 = (heightPx / 2.0).str()

        cssClass =
          COLORS[(delayedPacket.frameData.single().frameNumber % COLORS.size.toLong()).toInt()]
      }
    }
  }

  fun registerPacketToClient(now: Duration, client: Int, delayedPacket: DelayedPacket) {
    if (client in clientsDoneTracking) return

    val x = timeToX(delayedPacket.arrivalTime)
    maxX = max(x, maxX)
    svgActions.add {
      line {
        x1 = timeToX(now).str()
        x2 = x.toString()
        y1 = (heightPx / 2.0).str()
        y2 = yCoordOfClientId(client).str()

        cssClass =
          COLORS[(delayedPacket.frameData.first().frameNumber % COLORS.size.toLong()).toInt()]
      }
    }
  }

  private fun yCoordOfClientId(id: Int): Int {
    // Technically 1 is the server which is in the middle
    val boxNo = if (id == 0) 0 else 2
    val heightOfClientSection: Double = heightPx.toDouble() / 3.0

    return (boxNo.toDouble() * heightOfClientSection + heightOfClientSection / 2.0).roundToInt()
  }

  fun registerNewFrame(now: Duration, client: Int, frameNumber: Long) {
    if (client in clientsDoneTracking) return

    svgActions.add {
      line {
        x1 = timeToX(now).str()
        x2 = timeToX(now).str()
        y1 = (yCoordOfClientId(client) - 20).str()
        y2 = (yCoordOfClientId(client) + 20).str()

        cssClass = COLORS[(frameNumber % COLORS.size.toLong()).toInt()]
      }

      text {
        x = (timeToX(now) - 10).str()
        y =
          (yCoordOfClientId(client) +
              when (client) {
                0 -> -40
                1 -> 60
                else -> TODO()
              })
            .str()
        body = frameNumber.str()
        fontFamily = "monospace"
        fontSize = "40px"
      }
    }
    if (frameNumber >= framesToDraw) clientsDoneTracking.add(client)
  }

  fun registerServerWait(start: Duration, end: Duration, frameNumber: Long) {
    if (clientsDoneTracking.size == 2) return

    svgActions.add {
      line {
        x1 = timeToX(start).str()
        x2 = timeToX(end).str()
        y1 = (heightPx / 2.0).toInt().str()
        y2 = (heightPx / 2.0).toInt().str()

        cssClass = COLORS[(frameNumber % COLORS.size.toLong()).toInt()]
      }
    }
  }

  fun registerClientWaitForFrame(start: Duration, client: Int, end: Duration, frameNumber: Long) {
    if (client in clientsDoneTracking) return

    svgActions.add {
      line {
        x1 = timeToX(start).str()
        x2 = timeToX(end).str()
        y1 = yCoordOfClientId(client).str()
        y2 = yCoordOfClientId(client).str()

        cssClass = COLORS[(frameNumber % COLORS.size.toLong()).toInt()]
      }
    }
  }

  fun draw() {
    val svg =
      SVG.svg(true) {
        style {
          body =
            """  
                 svg  { background-color:#ffffff }
                 path { fill:#fff; }
                 svg .grey-stroke { stroke: #a0a0a0; stroke-width: 5; }
                 
                 svg .red-stroke { stroke: red; stroke-width: 5; }
                 svg .orange-stroke { stroke: orange; stroke-width: 5; }
                 svg .yellow-stroke { stroke: yellow; stroke-width: 5; }
                 svg .green-stroke { stroke: green; stroke-width: 5; }
                 svg .blue-stroke { stroke: blue; stroke-width: 5; }
                 svg .indigo-stroke { stroke: indigo; stroke-width: 5; }
                 svg .violet-stroke { stroke: violet; stroke-width: 5; }
                 
                 svg .black-stroke { stroke: blue; stroke-width: 5; }
                 svg .gold-stroke { stroke: gold; stroke-width: 5; }
                 svg .fur-color { fill: blue; }
             """
              .trimIndent()
        }

        height = heightPx.str() // "300"
        width = (maxX + 1).str()

        fun SVG.horizontalLine(y: Int) {
          line {
            x1 = timeToX(Duration.ZERO).str()
            x2 = maxX.str()
            y1 = y.str()
            y2 = y.str()
            cssClass = "grey-stroke"
          }
        }
        println("THE MAX IS $maxX")

        horizontalLine(heightPx / 2)
        horizontalLine(yCoordOfClientId(0))
        horizontalLine(yCoordOfClientId(1))

        text {
          x = 0.str()
          y = yCoordOfClientId(0).str()
          body = "Client 1"
          fontFamily = "monospace"
          fontSize = "40px"
        }

        text {
          x = 0.str()
          y = (heightPx / 2.0).str()
          body = "Server"
          fontFamily = "monospace"
          fontSize = "40px"
        }

        text {
          x = 0.str()
          y = yCoordOfClientId(2).str()
          body = "Client 2"
          fontFamily = "monospace"
          fontSize = "40px"
        }

        for (it in svgActions) it()
      }

    FileWriter("diagram.svg").use { svg.render(it, RenderMode.FILE) }
  }

  private companion object {
    const val INITIAL_FRAME_WIDTH_PX = 150
    const val PIXELS_PER_MILLISECOND = INITIAL_FRAME_WIDTH_PX / (1_000.0 / 60.0)

    val COLORS = arrayOf("red", "green", "blue").map { "$it-stroke" }
  }
}
