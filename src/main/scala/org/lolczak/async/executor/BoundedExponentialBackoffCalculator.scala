package org.lolczak.async.executor

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

class BoundedExponentialBackoffCalculator(baseSleepTime: FiniteDuration, maxSleepTime: FiniteDuration, randomizationFactor: Double)
  extends BackoffTimeCalculator {

  private val decoratee = new ExponentialBackoffCalculator(baseSleepTime, randomizationFactor)

  override def evalBackoffTime(retryCount: Int, elapsedTime: FiniteDuration): FiniteDuration = {
    val computedTime = decoratee.evalBackoffTime(retryCount, elapsedTime).toMillis
    val finalTime = Math.min(maxSleepTime.toMillis, computedTime)
    FiniteDuration(finalTime, TimeUnit.MILLISECONDS)
  }

}
