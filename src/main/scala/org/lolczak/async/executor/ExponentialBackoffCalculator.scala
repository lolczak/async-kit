package org.lolczak.async.executor

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.Random

class ExponentialBackoffCalculator(baseSleepTime: FiniteDuration, randomizationFactor: Double) extends BackoffTimeCalculator {
  require(randomizationFactor >= 0 && randomizationFactor < 1)
  require(baseSleepTime.toMillis > 0)

  val random = new Random()

  override def evalBackoffTime(retryCount: Int, elapsedTime: FiniteDuration): FiniteDuration = {
    require(retryCount > 0)
    val exp = Math.min(31, retryCount) - 1
    val retryInterval = (1 << exp) * baseSleepTime.toMillis
    val delta = randomizationFactor * retryInterval
    val minInterval = retryInterval - delta
    val maxInterval = retryInterval + delta
    val randomizedInterval = minInterval.toInt + random.nextInt(maxInterval.toInt - minInterval.toInt + 1)
    val finalInterval = Math.max(1, randomizedInterval)
    FiniteDuration(finalInterval, TimeUnit.MILLISECONDS)
  }

}
