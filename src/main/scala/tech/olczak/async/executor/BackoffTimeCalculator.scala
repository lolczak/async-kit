package tech.olczak.async.executor

import scala.concurrent.duration.FiniteDuration

trait BackoffTimeCalculator {

  def evalBackoffTime(retryCount: Int, elapsedTime: FiniteDuration): FiniteDuration

}
