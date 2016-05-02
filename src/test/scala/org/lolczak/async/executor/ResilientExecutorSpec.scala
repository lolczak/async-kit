package org.lolczak.async.executor

import java.util.concurrent.TimeUnit

import org.lolczak.async.AsyncAction._
import org.lolczak.async.Failure
import org.lolczak.async.error.RecoverableErrorMatcher
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{Matchers, FlatSpec}

import scala.concurrent.duration.FiniteDuration
import scalaz.concurrent.Strategy

class ResilientExecutorSpec extends FlatSpec with Matchers with ScalaFutures {

  "A resilient executor" should "retry at most 3 times" in new TestContext {
    var count = 0
    val action = delay { count += 1 } >> raiseError(new Exception)
    objectUnderTest.execute(action).futureValue
    count shouldBe 4
  }

  it should "stop after execution limit" in new TestContext {
    override lazy val executionLimit = FiniteDuration(10, TimeUnit.MILLISECONDS)
    var count = 0
    val action = delay { count += 1 } >> raiseError(new Exception)
    objectUnderTest.execute(action).futureValue
    count shouldBe 2
  }

  it should "retry after exponential time" in new TestContext {
    val start = System.currentTimeMillis()
    val result = objectUnderTest.execute(raiseError(Failure("err"))).futureValue
    val duration = System.currentTimeMillis() - start
    duration should be >= (10l +  20l + 40l)
  }

  it should "not retry when error is not recoverable" in new TestContext {
    implicit val errorMatcher: RecoverableErrorMatcher[Throwable] = (error: Throwable) => error.isInstanceOf[RuntimeException]
    var count = 0
    val action = delay { count += 1 } >> raiseError(new Exception)
    objectUnderTest.execute(action).futureValue
    count shouldBe 1
  }

  trait TestContext {
    implicit val pool = Strategy.DefaultTimeoutScheduler

    implicit val patienceConfig = PatienceConfig(timeout = scaled(Span(1500, Millis)), interval = scaled(Span(15, Millis)))

    lazy val executionLimit = FiniteDuration(1, TimeUnit.MINUTES)

    lazy val objectUnderTest = new ResilientExecutor(3, executionLimit, new ExponentialBackoffCalculator(FiniteDuration(10, TimeUnit.MILLISECONDS), 0))

  }



}
