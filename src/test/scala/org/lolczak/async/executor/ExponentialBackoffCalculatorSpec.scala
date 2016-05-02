package org.lolczak.async.executor

import java.util.concurrent.TimeUnit

import org.scalatest.FlatSpec

import scala.concurrent.duration.FiniteDuration

class ExponentialBackoffCalculatorSpec extends FlatSpec {

  it should " work" in {
    val calculator = new ExponentialBackoffCalculator(FiniteDuration(1, TimeUnit.SECONDS), 0)
    val randCalculator = new ExponentialBackoffCalculator(FiniteDuration(1, TimeUnit.SECONDS), 0.75)
    (1 to 100) foreach { retryCount =>
      println(s"retry= $retryCount interval=${calculator.evalBackoffTime(retryCount, FiniteDuration(1, TimeUnit.SECONDS))}, randInt=${randCalculator.evalBackoffTime(retryCount, FiniteDuration(1, TimeUnit.SECONDS))}")
    }
  }

}
