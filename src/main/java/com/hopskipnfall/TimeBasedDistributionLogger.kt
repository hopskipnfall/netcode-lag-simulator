package com.hopskipnfall

import kotlin.time.Duration
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

class TimeBasedDataLogger(
  private val timestampTransformer: (Duration) -> Double,
  private val timeName: String = "Timstamp (ms)",
) {
  private val times = mutableListOf<Double>()
  private val data = mutableMapOf<String, MutableList<Any>>()

  private var lastTime: Duration? = null

  fun addRow(time: Duration, vararg columnToValues: Pair<String, Any>) {
    columnToValues.forEach { (colName, _) ->
      if (data[colName] == null) data[colName] = mutableListOf()
    }

    times.add(timestampTransformer(time))
    lastTime = time

    for ((column: String, value) in columnToValues) {
      var columnData = data[column]
      if (columnData == null) {
        columnData = mutableListOf()
        data[column] = columnData
      }
      columnData.add(value)
    }
  }

  fun buildDataFrame() =
    dataFrameOf(
      timeName to times,
      *(data.map { (columnName, values) -> columnName to values }.toTypedArray())
    )
}
